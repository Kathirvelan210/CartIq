package com.smartcart;

import com.smartcart.catalogimport.ProductMatching;
import com.smartcart.catalogimport.ProductMatching.Identity;
import org.junit.jupiter.api.Test;

import static com.smartcart.catalogimport.ProductMatching.Decision.*;
import static org.junit.jupiter.api.Assertions.*;

class ProductMatchingTest {
    @Test
    void equivalentBarcodeAndPackReturnExactMatch() {
        var left = new Identity("8901719134852", " PARLE ", "Parle-G", "90 g", null, null);
        var right = new Identity("08901719134852", "parle", "PARLE G", "0.09 kg", "Original", null);
        var result = ProductMatching.compare(left, right);
        assertEquals(EXACT_GTIN, result.decision());
        assertEquals(100, result.score());
        assertEquals(1, result.nameSimilarity());
        assertTrue(result.reasons().contains("GTIN_MATCH"));
    }

    @Test
    void sameGtinWithConflictRequiresReview() {
        var left = new Identity("8901719134852", "Parle", "Parle-G", "90 g", "Original", null);
        var right = new Identity("8901719134852", "Parle", "Parle-G", "45 g", "Chocolate", null);
        var result = ProductMatching.compare(left, right);
        assertEquals(REVIEW, result.decision());
        assertTrue(result.reasons().containsAll(java.util.List.of("PACK_DIFFERENT", "VARIANT_DIFFERENT")));
    }

    @Test
    void similarNamesWithoutMatchingGtinDoNotEstablishIdentity() {
        var left = new Identity("8901764042911", "Thums Up", "Thums Up", "250 ml", null, null);
        var right = new Identity("3948764042911", "THUMSUP", "Thums Up", "0.25 litre", null, null);
        var result = ProductMatching.compare(left, right);
        assertEquals(REVIEW, result.decision());
        assertTrue(result.reasons().containsAll(java.util.List.of("GTIN_CONFLICT", "BRAND_SAME")));
    }

    @Test
    void formulationAndUnitDifferencesRemainDistinct() {
        var left = new Identity(null, "Fixture", "Chocolate dessert", "250 g", "Original", "Contains sugar");
        var formulation = new Identity(null, "Fixture", "Chocolate dessert", "250 g", "Original", "No added sugar");
        var unit = new Identity(null, "Fixture", "Chocolate dessert", "250 ml", "Original", "Contains sugar");
        assertEquals(DISTINCT, ProductMatching.compare(left, formulation).decision());
        assertEquals(DISTINCT, ProductMatching.compare(left, unit).decision());
    }

    @Test
    void multilingualTextIsPreservedAndAmbiguousBrandGoesToReview() {
        var left = new Identity(null, "फिक्स्चर", "मसाला चाय", "25 g", null, null);
        var partial = new Identity(null, "फिक्स्चर", "मसाला चाय पत्ती", "25 g", null, null);
        var ambiguous = new Identity(null, "फिक्स्चर, Brand", "मसाला चाय", "25 g", null, null);
        var result = ProductMatching.compare(left, partial);
        assertEquals(REVIEW, result.decision());
        assertEquals(2.0 / 3, result.nameSimilarity());
        assertEquals(INSUFFICIENT_INFORMATION, ProductMatching.compare(left, ambiguous).decision());
    }

    @Test
    void invalidGtinOrUnparseablePackIsNotSupportingEvidence() {
        var left = new Identity(null, "Fixture", "Rice", "500 g", null, null);
        var right = new Identity("8901719134853", "Fixture", "Rice", "500", null, null);
        var result = ProductMatching.compare(left, right);
        assertEquals(INSUFFICIENT_INFORMATION, result.decision());
        assertTrue(result.reasons().containsAll(java.util.List.of("INVALID_GTIN", "PACK_MISSING")));
    }

    @Test
    void matchesMetricAliasesAndPreservesMultipackCount() {
        var original = new Identity("8901719134845", "Parle", "Parle-G", "45gm", "Original", null);
        var normalized = new Identity("08901719134845", "Parle", "Parle G", "0.045 kg", "Original", null);
        var multi = new Identity(null, "Parle", "Parle G", "2 × 45 g", "Original", null);
        var single = new Identity(null, "Parle", "Parle G", "90 g", "Original", null);
        assertEquals(EXACT_GTIN, ProductMatching.compare(original, normalized).decision());
        assertEquals(DISTINCT, ProductMatching.compare(multi, single).decision());
        assertTrue(ProductMatching.compare(multi, single).reasons().contains("PACK_DIFFERENT"));
    }

    @Test
    void sourceAssignedCodeRequiresReviewEvenWhenOtherFieldsMatch() {
        var identity = new Identity("2000000000008", "Fixture", "Rice", "1ltr", null, null);
        var other = new Identity("02000000000008", "Fixture", "Rice", "1 l", null, null);
        var result = ProductMatching.compare(identity, other);
        assertEquals(REVIEW, result.decision());
        assertTrue(result.reasons().containsAll(java.util.List.of("RESTRICTED_GTIN", "PACK_SAME")));
    }

    @Test
    void hybridNormalizesAnExplicitBrandAndPackInTheTitle() {
        var left = new Identity("8901719134852", "Parle", "Parle-G", "90 g", null, null);
        var right = new Identity("08901719134852", "PARLE", "Parle Parle G 0.09 kg", "90 gm", null, null);
        assertEquals(REVIEW, ProductMatching.compare(left, right).decision());
        assertEquals(EXACT_GTIN, ProductMatching.compareHybrid(left, right).decision());
    }

    @Test
    void hybridRequiresSpecificCorroborationForASingleGtin() {
        var left = new Identity("8901719134852", "Fixture", "Cashew Cookies", "90 g",
            "Cashew", "Wheat cashew recipe");
        var same = new Identity(null, "Fixture", "Cashew Cookies", "90 g", "Cashew", "Wheat cashew recipe");
        var vague = new Identity(null, "Fixture", "Cashew Cookies", "90 g", "Cashew", null);
        var invalid = new Identity("8901719134853", same.brand(), same.name(), same.pack(), same.variant(), same.formulation());
        assertEquals(SUPPORTED_MATCH, ProductMatching.compareHybrid(left, same).decision());
        assertEquals(REVIEW, ProductMatching.compareHybrid(left, vague).decision());
        assertEquals(REVIEW, ProductMatching.compareHybrid(left, invalid).decision());
    }

    @Test
    void hybridWillNotTreatConflictingPackStructureOrFormulationAsEquivalent() {
        var left = new Identity("8901719134852", "Fixture", "Cookies", "90 g", "Cashew", "Original wheat");
        var multipack = new Identity("8901719134852", "Fixture", "Cookies", "2 x 45 g", "Cashew", "Original wheat");
        var formulation = new Identity(null, "Fixture", "Cookies", "90 g", "Cashew", "Gluten free");
        assertEquals(REVIEW, ProductMatching.compareHybrid(left, multipack).decision());
        assertEquals(DISTINCT, ProductMatching.compareHybrid(left, formulation).decision());
    }
}
