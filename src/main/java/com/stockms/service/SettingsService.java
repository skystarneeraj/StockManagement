package com.stockms.service;

import com.stockms.entity.CompanySettings;
import com.stockms.repository.CompanySettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class SettingsService {

    public static final int MAX_LOGO_BYTES = 500 * 1024;

    private final CompanySettingsRepository repository;

    public SettingsService(CompanySettingsRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public CompanySettings get() {
        return repository.findById(CompanySettings.ID).orElseGet(CompanySettings::new);
    }

    /** Saves the details and keeps the current logo. */
    public CompanySettings save(CompanySettings settings) {
        return save(settings, null, false);
    }

    /**
     * Saves the details. A non-null {@code newLogo} replaces the logo (check it with
     * {@link #imageType} first); otherwise the current logo is kept unless {@code removeLogo}.
     */
    public CompanySettings save(CompanySettings settings, byte[] newLogo, boolean removeLogo) {
        settings.setId(CompanySettings.ID);
        if (settings.getState() == null) settings.setState(Gst.stateFromGstin(settings.getGstin()));
        if (newLogo != null) {
            settings.setLogo(newLogo);
            settings.setLogoType(imageType(newLogo));
        } else if (removeLogo) {
            settings.setLogo(null);
            settings.setLogoType(null);
        } else {
            CompanySettings current = get();
            settings.setLogo(current.getLogo());
            settings.setLogoType(current.getLogoType());
        }
        return repository.save(settings);
    }

    /**
     * Media type of a PNG, JPEG, GIF or WebP image, judged from its first bytes rather than
     * the uploaded file name; null for anything else (SVG is refused because it can carry scripts).
     */
    public static String imageType(byte[] b) {
        if (startsWith(b, 0, 0x89, 'P', 'N', 'G')) return "image/png";
        if (startsWith(b, 0, 0xFF, 0xD8, 0xFF)) return "image/jpeg";
        if (startsWith(b, 0, 'G', 'I', 'F', '8')) return "image/gif";
        if (startsWith(b, 0, 'R', 'I', 'F', 'F') && startsWith(b, 8, 'W', 'E', 'B', 'P')) return "image/webp";
        return null;
    }

    private static boolean startsWith(byte[] b, int offset, int... prefix) {
        if (b == null || b.length < offset + prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) {
            if ((b[offset + i] & 0xFF) != prefix[i]) return false;
        }
        return true;
    }
}
