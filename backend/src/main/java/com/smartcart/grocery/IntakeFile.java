package com.smartcart.grocery;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartcart.exception.ApiException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

final class IntakeFile {
    static final int MAX_BYTES=524288;
    static final int MAX_ROWS=500;
    private static final Set<String> REQUIRED=Set.of("dataClass","sourceRecordId","sourceEvidence","observedAt","variantId",
        "retailer","locationId","retailerSku","pack","currency","priceMinor","stock");
    private static final Set<String> OPTIONAL=Set.of("mrpMinor","discountPercent","sourceAvailableAt","sourceAsOfEvidence");
    private IntakeFile() {}

    static List<JsonNode> parse(ObjectMapper mapper,byte[] bytes, String format) {
        if (bytes.length==0||bytes.length>MAX_BYTES) throw ApiException.bad("Submit a UTF-8 file of at most 512 KiB.");
        try {
            String text=StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
            if (text.startsWith("\uFEFF")) text=text.substring(1);
            List<JsonNode> rows;
            if ("CSV".equals(format)) rows=csv(mapper,text);
            else if ("JSON".equals(format)) {
                try(var parser=mapper.createParser(text).enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)) {
                    JsonNode root=mapper.readTree(parser);
                    if (root!=null&&root.isObject()&&root.size()==1) root=root.get("rows");
                    if (root==null||!root.isArray()||parser.nextToken()!=null) throw ApiException.bad("Use a JSON rows array or an object containing only rows.");
                    rows=new ArrayList<>(); root.forEach(rows::add);
                }
            } else throw ApiException.bad("Select CSV or JSON.");
            if (rows.isEmpty()||rows.size()>MAX_ROWS) throw ApiException.bad("An import must contain between 1 and 500 rows.");
            return rows;
        } catch (IOException invalid) { throw ApiException.bad("The input is not valid UTF-8 or structured data."); }
    }

    static boolean rowShape(JsonNode row) {
        if (!row.isObject()) return false;
        for (var field:REQUIRED) if (!row.hasNonNull(field)) return false;
        for (var fields=row.fields();fields.hasNext();) {
            var entry=fields.next(); var key=entry.getKey(); var value=entry.getValue();
            if (!REQUIRED.contains(key)&&!OPTIONAL.contains(key)) return false;
            if (value.isNull()&&OPTIONAL.contains(key)) continue;
            if (key.equals("priceMinor")||key.equals("mrpMinor")) {
                if (!value.isIntegralNumber()||!value.canConvertToLong()) return false;
            } else if (key.equals("discountPercent")) {
                if (!value.isNumber()) return false;
            } else if (!value.isTextual()) return false;
        }
        return true;
    }

    private static List<JsonNode> csv(ObjectMapper mapper,String text) {
        List<List<String>> records=new ArrayList<>(); List<String> row=new ArrayList<>();
        var field=new StringBuilder(); boolean quoted=false,closed=false;
        for (int i=0;i<text.length();i++) {
            char ch=text.charAt(i);
            if (quoted) {
                if (ch=='"') {
                    if (i+1<text.length()&&text.charAt(i+1)=='"') { field.append('"');i++; }
                    else { quoted=false;closed=true; }
                } else field.append(ch);
            } else if (ch==','||ch=='\r'||ch=='\n') {
                row.add(field.toString()); field.setLength(0); closed=false;
                if (ch==',') continue;
                if (ch=='\r'&&i+1<text.length()&&text.charAt(i+1)=='\n') i++;
                records.add(row);row=new ArrayList<>();
                if (records.size()>MAX_ROWS+1) throw ApiException.bad("An import supports at most 500 rows.");
            } else if (closed) throw ApiException.bad("Invalid quoted CSV value.");
            else if (ch=='"') {
                if (!field.isEmpty()) throw ApiException.bad("Invalid CSV quoting.");
                quoted=true;
            } else field.append(ch);
        }
        if (quoted) throw ApiException.bad("Unclosed CSV quote.");
        if (!field.isEmpty()||!row.isEmpty()||closed) {row.add(field.toString());records.add(row);}
        if (records.isEmpty()) throw ApiException.bad("The CSV header is missing.");
        var header=records.remove(0);
        var columns=new HashSet<>(header);
        if (columns.size()!=header.size()||!columns.containsAll(REQUIRED)||columns.stream().anyMatch(c->!REQUIRED.contains(c)&&!OPTIONAL.contains(c))) {
            throw ApiException.bad("Use the documented CSV header with unique field names.");
        }
        List<JsonNode> output=new ArrayList<>();
        for (var record:records) {
            if (record.size()!=header.size()) {output.add(mapper.nullNode());continue;}
            ObjectNode values=mapper.createObjectNode();
            for (int i=0;i<header.size();i++) {
                var key=header.get(i); var value=record.get(i);
                if (value.isEmpty()&&OPTIONAL.contains(key)) continue;
                try {
                    switch(key) {
                        case "priceMinor","mrpMinor" -> values.put(key,Long.parseLong(value));
                        case "discountPercent" -> values.put(key,new java.math.BigDecimal(value));
                        default -> values.put(key,value);
                    }
                } catch (NumberFormatException invalid) { values.put(key,value); }
            }
            output.add(values);
        }
        return output;
    }

    static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
