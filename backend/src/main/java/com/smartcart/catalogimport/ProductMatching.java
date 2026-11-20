package com.smartcart.catalogimport;

import com.smartcart.exception.ApiException;
import jakarta.validation.constraints.Size;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Advisory evidence for a reviewer. It does not change products, variants or retailer mappings. */
public final class ProductMatching {
    private static final Pattern WORD_BOUNDARY = Pattern.compile("[^\\p{L}\\p{N}\\p{M}]+");
    private static final Pattern MULTIPACK = Pattern.compile("(?i)^([1-9][0-9]{0,4})\\s*[x×]\\s*(.+)$");
    private static final Pattern UNIT_ALIAS = Pattern.compile("(?i)\\s*(gm|gms|ltr|ltrs|grm|mls)\\s*$");
    private static final Set<String> MISSING_LABELS = Set.of("", "n a", "unknown", "unspecified");

    private ProductMatching() {}

    public record Identity(@Size(max = 14) String gtin, @Size(max = 100) String brand,
                           @Size(max = 150) String name, @Size(max = 60) String pack,
                           @Size(max = 100) String variant, @Size(max = 150) String formulation) {}
    public enum Decision { EXACT_GTIN, SUPPORTED_MATCH, REVIEW, DISTINCT, INSUFFICIENT_INFORMATION }
    public enum Confidence { HIGH, MEDIUM, LOW }
    public record Result(Decision decision, Confidence confidence, int score, double nameSimilarity,
                         List<String> reasons) {}

    public static Result compare(Identity left, Identity right) {
        List<String> reasons = new ArrayList<>();
        String leftGtin = barcode(left.gtin(), reasons);
        String rightGtin = barcode(right.gtin(), reasons);
        boolean sameGtin = leftGtin != null && leftGtin.equals(rightGtin);
        boolean differentGtins = leftGtin != null && rightGtin != null && !sameGtin;
        boolean invalidGtin = reasons.contains("INVALID_GTIN");
        boolean restrictedGtin = reasons.contains("RESTRICTED_GTIN");
        if (sameGtin) reasons.add("GTIN_MATCH");
        if (differentGtins) reasons.add("GTIN_CONFLICT");

        Field brand = compareBrand(left.brand(), right.brand());
        Field name = compareText(left.name(), right.name());
        Field variant = compareText(left.variant(), right.variant());
        Field formulation = compareText(left.formulation(), right.formulation());
        var leftPack = pack(left.pack());
        var rightPack = pack(right.pack());
        Field pack = comparePacks(leftPack, rightPack);
        double similarity = name == Field.MISSING ? 0 : tokenSimilarity(left.name(), right.name());

        addReason(reasons, "BRAND", brand);
        addReason(reasons, "NAME", name);
        addReason(reasons, "PACK", pack);
        addReason(reasons, "VARIANT", variant);
        addReason(reasons, "FORMULATION", formulation);
        int score = (int) Math.round(40 * similarity) + points(brand, 25) + points(pack, 20)
            + points(variant, 10) + points(formulation, 5);

        boolean completeIdentity = brand == Field.SAME && name == Field.SAME && pack == Field.SAME;
        boolean variantDifference = pack == Field.DIFFERENT || variant == Field.DIFFERENT || formulation == Field.DIFFERENT;

        if (sameGtin) {
            if (!restrictedGtin && completeIdentity && !variantDifference)
                return result(Decision.EXACT_GTIN, Confidence.HIGH, 100, similarity, reasons);
            reasons.add("CHECK_SOURCE_LABELS_FOR_SHARED_GTIN");
            return result(Decision.REVIEW, Confidence.HIGH, score, similarity, reasons);
        }
        if (variantDifference || brand == Field.DIFFERENT) {
            return result(Decision.DISTINCT, Confidence.HIGH, score, similarity, reasons);
        }
        if (brand == Field.MISSING || name == Field.MISSING || pack == Field.MISSING || invalidGtin || restrictedGtin) {
            return result(Decision.INSUFFICIENT_INFORMATION, Confidence.LOW, score, similarity, reasons);
        }
        if (similarity >= 0.5) {
            return result(Decision.REVIEW, completeIdentity ? Confidence.HIGH : Confidence.MEDIUM, score, similarity, reasons);
        }
        return result(Decision.DISTINCT, Confidence.MEDIUM, score, similarity, reasons);
    }

    /** Conservative advisory comparison for independently described listings. */
    public static Result compareHybrid(Identity left, Identity right) {
        List<String> reasons = new ArrayList<>();
        String leftGtin = barcode(left.gtin(), reasons), rightGtin = barcode(right.gtin(), reasons);
        boolean suspectGtin = reasons.contains("INVALID_GTIN") || reasons.contains("RESTRICTED_GTIN");
        boolean sharedGtin = leftGtin != null && leftGtin.equals(rightGtin);
        boolean conflictingGtin = leftGtin != null && rightGtin != null && !sharedGtin;
        if (sharedGtin) reasons.add("GTIN_MATCH");
        if (conflictingGtin) reasons.add("GTIN_CONFLICT");

        Field brand = compareBrand(left.brand(), right.brand());
        Field quantity = comparePacks(pack(left.pack()), pack(right.pack()));
        Field variant = compareText(left.variant(), right.variant());
        Field formulation = compareText(left.formulation(), right.formulation());
        String leftTitle = comparisonTitle(left), rightTitle = comparisonTitle(right);
        Field title = compareText(leftTitle, rightTitle);
        if (title == Field.DIFFERENT && brand == Field.SAME
            && (withoutBrandPrefix(leftTitle, left.brand()).equals(rightTitle)
                || withoutBrandPrefix(rightTitle, right.brand()).equals(leftTitle))) title = Field.SAME;
        double similarity = title == Field.SAME ? 1
            : title == Field.MISSING ? 0 : tokenSimilarity(leftTitle, rightTitle);
        boolean specificVariant = variant == Field.SAME && isSpecific(left.variant());
        boolean specificFormulation = formulation == Field.SAME && isSpecific(left.formulation());
        boolean explicitConflict = brand == Field.DIFFERENT || quantity == Field.DIFFERENT
            || variant == Field.DIFFERENT || formulation == Field.DIFFERENT;
        addReason(reasons, "BRAND", brand);
        addReason(reasons, "PACK", quantity);
        addReason(reasons, "VARIANT", variant);
        addReason(reasons, "FORMULATION", formulation);
        addReason(reasons, "TITLE", title);
        int score = (int) Math.round(40 * similarity) + points(brand, 25) + points(quantity, 20)
            + points(variant, 10) + points(formulation, 5);

        if (suspectGtin || (sharedGtin && explicitConflict)) {
            reasons.add("CHECK_BARCODE_AND_LABEL");
            return result(Decision.REVIEW, Confidence.HIGH, score, similarity, reasons);
        }
        if (explicitConflict) return result(Decision.DISTINCT, Confidence.HIGH, score, similarity, reasons);

        boolean compatibleTitle = title == Field.SAME
            || (similarity >= 0.75 && specificVariant && specificFormulation);
        if (sharedGtin && brand == Field.SAME && quantity == Field.SAME && compatibleTitle)
            return result(Decision.EXACT_GTIN, Confidence.HIGH, 100, similarity, reasons);
        if (conflictingGtin || sharedGtin) {
            reasons.add("CHECK_BARCODE_AND_LABEL");
            return result(Decision.REVIEW, Confidence.HIGH, score, similarity, reasons);
        }
        if ((leftGtin != null || rightGtin != null) && brand == Field.SAME && quantity == Field.SAME
            && title == Field.SAME && specificVariant && specificFormulation) {
            reasons.add("SINGLE_GTIN_WITH_EXPLICIT_IDENTITY");
            return result(Decision.SUPPORTED_MATCH, Confidence.MEDIUM, score, similarity, reasons);
        }
        if (brand == Field.MISSING || quantity == Field.MISSING || title == Field.MISSING)
            return result(Decision.INSUFFICIENT_INFORMATION, Confidence.LOW, score, similarity, reasons);
        return result(Decision.REVIEW, Confidence.LOW, score, similarity, reasons);
    }

    private static boolean isSpecific(String value) {
        return !Set.of("", "standard", "regular", "original", "plain", "classic", "default")
            .contains(normalized(value));
    }

    private static String comparisonTitle(Identity identity) {
        String title = identity.name();
        if (title == null) return "";
        String folded = Normalizer.normalize(title, Normalizer.Form.NFKC).strip();
        // Remove a trailing pack label only when it agrees with the separately declared pack.
        Pack declared = pack(identity.pack());
        for (int i = 1; declared != null && i < folded.length() - 1; i++) {
            if (Character.isWhitespace(folded.charAt(i))
                && comparePacks(declared, pack(folded.substring(i + 1))) == Field.SAME) {
                folded = folded.substring(0, i);
                break;
            }
        }
        return normalized(folded);
    }

    private static String withoutBrandPrefix(String title, String brandName) {
        String brand = normalized(brandName);
        return !brand.isEmpty() && title.startsWith(brand + " ") ? title.substring(brand.length() + 1) : title;
    }

    private enum Field { SAME, DIFFERENT, MISSING }

    private static Result result(Decision decision, Confidence confidence, int score, double similarity,
                                 List<String> reasons) {
        return new Result(decision, confidence, score, similarity, List.copyOf(reasons));
    }

    private static int points(Field field, int weight) {
        return field == Field.SAME ? weight : 0;
    }

    private static void addReason(List<String> reasons, String key, Field field) {
        reasons.add(key + "_" + field);
    }

    private static Field compareText(String left, String right) {
        String a = normalized(left), b = normalized(right);
        if (MISSING_LABELS.contains(a) || MISSING_LABELS.contains(b)) return Field.MISSING;
        return a.equals(b) ? Field.SAME : Field.DIFFERENT;
    }

    private static Field compareBrand(String left, String right) {
        if (left == null || right == null || left.contains(",") || right.contains(",")) return Field.MISSING;
        String a = normalized(left), b = normalized(right);
        if (MISSING_LABELS.contains(a) || MISSING_LABELS.contains(b)) return Field.MISSING;
        return a.replace(" ", "").equals(b.replace(" ", "")) ? Field.SAME : Field.DIFFERENT;
    }

    public static String normalized(String value) {
        if (value == null) return "";
        String folded = Normalizer.normalize(value, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        return WORD_BOUNDARY.matcher(folded).replaceAll(" ").strip();
    }

    private static double tokenSimilarity(String left, String right) {
        var a = new HashSet<>(List.of(normalized(left).split(" ")));
        var b = new HashSet<>(List.of(normalized(right).split(" ")));
        int count = a.size();
        a.retainAll(b);
        return (double) a.size() / (count + b.size() - a.size());
    }

    private static String barcode(String value, List<String> reasons) {
        if (value == null || value.isBlank()) return null;
        try {
            String gtin = ImportNormalizer.gtin(value.strip());
            if (gtin.startsWith("0200") && !reasons.contains("RESTRICTED_GTIN")) reasons.add("RESTRICTED_GTIN");
            return gtin;
        }
        catch (ApiException error) { reasons.add("INVALID_GTIN"); return null; }
    }

    public record Pack(int count, ImportNormalizer.Pack each) {}

    private static Field comparePacks(Pack left, Pack right) {
        if (left == null || right == null) return Field.MISSING;
        return left.count() == right.count() && left.each().unit().equals(right.each().unit())
            && left.each().amount().compareTo(right.each().amount()) == 0 ? Field.SAME : Field.DIFFERENT;
    }

    public static Pack pack(String value) {
        if (value == null) return null;
        String label = Normalizer.normalize(value, Normalizer.Form.NFKC).strip();
        var multi = MULTIPACK.matcher(label);
        int count = 1;
        if (multi.matches()) {
            count = Integer.parseInt(multi.group(1));
            label = multi.group(2);
        }
        var alias = UNIT_ALIAS.matcher(label);
        if (alias.find()) {
            String unit = switch (alias.group(1).toLowerCase(Locale.ROOT)) {
                case "ltr", "ltrs" -> "l";
                case "mls" -> "ml";
                default -> "g";
            };
            label = label.substring(0, alias.start()) + " " + unit;
        }
        try { return new Pack(count, ImportNormalizer.pack(label)); }
        catch (ApiException error) { return null; }
    }
}
