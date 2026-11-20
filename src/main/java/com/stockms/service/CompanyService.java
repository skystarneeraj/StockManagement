package com.stockms.service;

import com.stockms.entity.AppUser;
import com.stockms.entity.Branch;
import com.stockms.entity.Company;
import com.stockms.repository.AppUserRepository;
import com.stockms.repository.BranchRepository;
import com.stockms.repository.CompanyRepository;
import com.stockms.tenant.TenantContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** The signed-in user's company profile, and company management for the super admin. */
@Service
@Transactional
public class CompanyService {

    public static final int MAX_LOGO_BYTES = 500 * 1024;

    private final CompanyRepository repository;
    private final BranchRepository branchRepository;
    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public CompanyService(CompanyRepository repository, BranchRepository branchRepository,
                          AppUserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.branchRepository = branchRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /** The current user's company. */
    @Transactional(readOnly = true)
    public Company current() {
        return repository.findById(TenantContext.require().companyId()).orElseThrow();
    }

    /** Saves the profile form onto the current company and keeps the current logo. */
    public Company saveProfile(Company form) {
        return saveProfile(form, null, false);
    }

    /**
     * Saves the profile form onto the current company (the form's id is ignored). A non-null
     * {@code newLogo} replaces the logo (check it with {@link #imageType} first); otherwise the
     * current logo is kept unless {@code removeLogo}.
     */
    public Company saveProfile(Company form, byte[] newLogo, boolean removeLogo) {
        Company company = current();
        copyDetails(form, company);
        company.setAllowNegativeStock(form.isAllowNegativeStock());
        if (newLogo != null) {
            company.setLogo(newLogo);
            company.setLogoType(imageType(newLogo));
        } else if (removeLogo) {
            company.setLogo(null);
            company.setLogoType(null);
        }
        return company;
    }

    /** True if another company already uses this code. */
    @Transactional(readOnly = true)
    public boolean codeTaken(String code, Long exceptCompanyId) {
        return code != null && repository.findByCodeIgnoreCase(code.trim())
                .filter(c -> !c.getId().equals(exceptCompanyId)).isPresent();
    }

    // ---- Super admin ----------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<Company> all() {
        return repository.findAllByOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public Company get(Long id) {
        return repository.findById(id).orElseThrow(() -> new BusinessException("Company not found."));
    }

    /** Creates a company with its Head Office branch and its first company admin. */
    public Company create(Company form, String adminUsername, String adminPassword) {
        if (codeTaken(form.getCode(), null)) throw new BusinessException("Company code " + form.getCode() + " is already used.");
        if (userRepository.findByUsernameIgnoreCase(adminUsername).isPresent()) {
            throw new BusinessException("Username " + adminUsername + " is already taken.");
        }
        Company company = new Company();
        copyDetails(form, company);
        company.setActive(true);
        repository.save(company);

        Branch headOffice = new Branch();
        headOffice.setCompanyId(company.getId());
        headOffice.setName("Head Office");
        headOffice.setCode("HO");
        headOffice.setAddress(company.getAddress());
        headOffice.setPhone(company.getPhone());
        headOffice.setEmail(company.getEmail());
        headOffice.setState(company.getState());
        headOffice.setHeadOffice(true);
        branchRepository.save(headOffice);

        AppUser admin = new AppUser(adminUsername, passwordEncoder.encode(adminPassword), AppUser.Role.COMPANY_ADMIN);
        admin.setCompany(company);
        admin.getBranches().add(headOffice);
        userRepository.save(admin);
        return company;
    }

    public Company update(Long id, Company form) {
        if (codeTaken(form.getCode(), id)) throw new BusinessException("Company code " + form.getCode() + " is already used.");
        Company company = get(id);
        copyDetails(form, company);
        return company;
    }

    public void setActive(Long id, boolean active) {
        get(id).setActive(active);
    }

    private static void copyDetails(Company from, Company to) {
        to.setName(from.getName().trim());
        to.setCode(from.getCode());
        to.setAddress(from.getAddress());
        to.setPhone(from.getPhone());
        to.setEmail(from.getEmail());
        to.setGstin(from.getGstin());
        to.setState(from.getState() != null ? from.getState() : Gst.stateFromGstin(from.getGstin()));
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
