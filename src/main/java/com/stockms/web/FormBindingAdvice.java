package com.stockms.web;

import org.springframework.beans.propertyeditors.StringTrimmerEditor;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.InitBinder;

/**
 * Trims form text and turns empty fields into null, so optional fields with a
 * format (GSTIN, HSN code) are simply left out instead of failing validation.
 */
@ControllerAdvice
public class FormBindingAdvice {

    @InitBinder
    void trimStrings(WebDataBinder binder) {
        binder.registerCustomEditor(String.class, new StringTrimmerEditor(true));
    }
}
