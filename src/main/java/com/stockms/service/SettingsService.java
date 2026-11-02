package com.stockms.service;

import com.stockms.entity.CompanySettings;
import com.stockms.repository.CompanySettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class SettingsService {

    private final CompanySettingsRepository repository;

    public SettingsService(CompanySettingsRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public CompanySettings get() {
        return repository.findById(CompanySettings.ID).orElseGet(CompanySettings::new);
    }

    public CompanySettings save(CompanySettings settings) {
        settings.setId(CompanySettings.ID);
        if (settings.getState() == null) settings.setState(Gst.stateFromGstin(settings.getGstin()));
        return repository.save(settings);
    }
}
