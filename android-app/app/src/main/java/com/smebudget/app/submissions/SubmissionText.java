package com.smebudget.app.submissions;

import android.content.Context;

import com.smebudget.app.R;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Turns API values into the words and money figures shown on the submission screens. */
final class SubmissionText {

    private static final String[] INSTANT_PATTERNS = {
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSSX",
            "yyyy-MM-dd'T'HH:mm:ssX",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'"
    };

    private SubmissionText() {
    }

    static String status(Context context, String status) {
        if (SubmissionStatus.DRAFT.equals(status)) {
            return context.getString(R.string.status_draft);
        } else if (SubmissionStatus.SUBMITTED.equals(status)) {
            return context.getString(R.string.status_submitted);
        } else if (SubmissionStatus.APPROVED.equals(status)) {
            return context.getString(R.string.status_approved);
        } else if (SubmissionStatus.REJECTED.equals(status)) {
            return context.getString(R.string.status_rejected);
        } else if (SubmissionStatus.SUPERSEDED.equals(status)) {
            return context.getString(R.string.status_superseded);
        }
        return status == null ? "" : status;
    }

    static String frequency(Context context, String frequency) {
        if ("monthly".equals(frequency)) {
            return context.getString(R.string.frequency_monthly);
        } else if ("quarterly".equals(frequency)) {
            return context.getString(R.string.frequency_quarterly);
        } else if ("annual".equals(frequency)) {
            return context.getString(R.string.frequency_annual);
        }
        return frequency == null ? "" : frequency;
    }

    static String importMethod(Context context, String method) {
        if ("template".equals(method)) {
            return context.getString(R.string.import_template);
        } else if ("mapped_import".equals(method)) {
            return context.getString(R.string.import_mapped);
        }
        return context.getString(R.string.import_manual);
    }

    static String scale(Context context, Integer sourceScale) {
        if (sourceScale != null && sourceScale == 1000) {
            return context.getString(R.string.scale_thousands);
        } else if (sourceScale != null && sourceScale == 1000000) {
            return context.getString(R.string.scale_millions);
        }
        return context.getString(R.string.scale_rands);
    }

    static String prettyDate(String iso) {
        if (iso == null || iso.length() < 10) {
            return "";
        }
        try {
            SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            parser.setLenient(false);
            Date date = parser.parse(iso.substring(0, 10));
            if (date == null) {
                return iso;
            }
            return new SimpleDateFormat("d MMM yyyy", Locale.ENGLISH).format(date);
        } catch (ParseException ex) {
            return iso;
        }
    }

    static String prettyInstant(String iso) {
        if (iso == null || iso.trim().length() == 0) {
            return "";
        }
        for (String pattern : INSTANT_PATTERNS) {
            try {
                SimpleDateFormat parser = new SimpleDateFormat(pattern, Locale.US);
                parser.setLenient(false);
                Date date = parser.parse(iso);
                if (date != null) {
                    return new SimpleDateFormat("d MMM yyyy, HH:mm", Locale.ENGLISH).format(date);
                }
            } catch (ParseException ex) {
                // Try the next pattern. Jackson's offset style varies.
            }
        }
        return iso;
    }

    static String money(BigDecimal amount) {
        if (amount == null) {
            return "";
        }
        NumberFormat format = NumberFormat.getCurrencyInstance(new Locale("en", "ZA"));
        format.setMinimumFractionDigits(2);
        format.setMaximumFractionDigits(2);
        return format.format(amount);
    }

    /**
     * Reads an amount typed by the user. Accepts a comma or a dot as the decimal mark.
     * Returns null when the text is empty, not a number, or has more than 2 decimal places.
     */
    static BigDecimal parseAmount(String raw) {
        if (raw == null) {
            return null;
        }
        String cleaned = raw.trim().replace(" ", "").replace("\u00a0", "");
        if (cleaned.length() == 0) {
            return null;
        }
        if (count(cleaned, ',') > 0 && count(cleaned, '.') > 0) {
            return null;
        }
        if (count(cleaned, ',') > 1 || count(cleaned, '.') > 1) {
            return null;
        }
        cleaned = cleaned.replace(',', '.');
        try {
            BigDecimal amount = new BigDecimal(cleaned);
            BigDecimal plain = amount.stripTrailingZeros();
            if (plain.scale() < 0) {
                plain = plain.setScale(0);
            }
            if (plain.scale() > 2 || plain.precision() - plain.scale() > 16) {
                return null;
            }
            return amount.setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static int count(String value, char needle) {
        int found = 0;
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) == needle) {
                found++;
            }
        }
        return found;
    }
}
