package com.stockms.config;

import com.stockms.service.Gst;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/** Display helpers for templates, used as ${@fmt.money(x)} and ${@fmt.date(d)}. */
@Component("fmt")
public class Fmt {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private final String currency;

    public Fmt(@Value("${app.currency-symbol:₹}") String currency) {
        this.currency = currency;
    }

    public String money(BigDecimal amount) {
        BigDecimal value = amount == null ? BigDecimal.ZERO : amount;
        return currency + " " + new DecimalFormat("#,##0.00").format(value);
    }

    public String date(LocalDate date) {
        return date == null ? "" : DATE.format(date);
    }

    /** A GST rate for display, e.g. "18%" or "0.25%". */
    public String rate(BigDecimal rate) {
        return Gst.rate(rate) + "%";
    }
}
