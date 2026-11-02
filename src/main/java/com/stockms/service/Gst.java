package com.stockms.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/** Indian GST rules used by bills, forms and reports. */
public final class Gst {

    /** 15-character GSTIN: state code, PAN, entity number, 'Z', checksum. */
    public static final String GSTIN_PATTERN = "[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]";

    /** HSN / SAC codes are 4, 6 or 8 digits. */
    public static final String HSN_PATTERN = "\\d{4}|\\d{6}|\\d{8}";

    /** Rates offered in forms; any rate from 0 to 100 can still be typed. */
    public static final List<BigDecimal> COMMON_RATES =
            Stream.of("0", "0.25", "3", "5", "12", "18", "28", "40").map(BigDecimal::new).toList();

    /** GST state codes (first two digits of a GSTIN) to state names. */
    public static final Map<String, String> STATES = new LinkedHashMap<>();

    static {
        String[][] states = {
                {"01", "Jammu and Kashmir"}, {"02", "Himachal Pradesh"}, {"03", "Punjab"}, {"04", "Chandigarh"},
                {"05", "Uttarakhand"}, {"06", "Haryana"}, {"07", "Delhi"}, {"08", "Rajasthan"},
                {"09", "Uttar Pradesh"}, {"10", "Bihar"}, {"11", "Sikkim"}, {"12", "Arunachal Pradesh"},
                {"13", "Nagaland"}, {"14", "Manipur"}, {"15", "Mizoram"}, {"16", "Tripura"},
                {"17", "Meghalaya"}, {"18", "Assam"}, {"19", "West Bengal"}, {"20", "Jharkhand"},
                {"21", "Odisha"}, {"22", "Chhattisgarh"}, {"23", "Madhya Pradesh"}, {"24", "Gujarat"},
                {"26", "Dadra and Nagar Haveli and Daman and Diu"}, {"27", "Maharashtra"}, {"29", "Karnataka"},
                {"30", "Goa"}, {"31", "Lakshadweep"}, {"32", "Kerala"}, {"33", "Tamil Nadu"},
                {"34", "Puducherry"}, {"35", "Andaman and Nicobar Islands"}, {"36", "Telangana"},
                {"37", "Andhra Pradesh"}, {"38", "Ladakh"}, {"97", "Other Territory"}};
        for (String[] s : states) STATES.put(s[0], s[1]);
    }

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private Gst() {
    }

    public static List<String> stateNames() {
        List<String> names = new ArrayList<>(STATES.values());
        names.sort(String::compareTo);
        return names;
    }

    /** The state encoded in a GSTIN, or null if the GSTIN is missing or malformed. */
    public static String stateFromGstin(String gstin) {
        if (gstin == null || !gstin.matches(GSTIN_PATTERN)) return null;
        return STATES.get(gstin.substring(0, 2));
    }

    /** GST on a taxable value, rounded to paise. */
    public static BigDecimal tax(BigDecimal taxable, BigDecimal ratePercent) {
        return taxable.multiply(ratePercent).divide(HUNDRED, 2, RoundingMode.HALF_UP);
    }

    /** CGST share of an intra-state tax amount; SGST is the remainder, so the two always add up. */
    public static BigDecimal cgstPart(BigDecimal tax) {
        return tax.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
    }

    /**
     * Supply between different states is charged IGST; within a state, CGST + SGST.
     * When either state is unknown the bill is treated as intra-state.
     */
    public static boolean isInterState(String ourState, String partyState) {
        return ourState != null && !ourState.isBlank() && partyState != null && !partyState.isBlank()
                && !ourState.trim().equalsIgnoreCase(partyState.trim());
    }

    /** "18", "0.25", "5" - for display next to a % sign. */
    public static String rate(BigDecimal rate) {
        return rate == null ? "0" : rate.stripTrailingZeros().toPlainString();
    }
}
