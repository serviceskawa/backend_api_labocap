package com.labo.anapath.hr;

import com.labo.anapath.common.exception.InvalidOperationException;
import com.labo.anapath.common.exception.ResourceNotFoundException;
import com.labo.anapath.setting.SettingApp;
import com.labo.anapath.setting.SettingAppRepository;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

/**
 * Génère le PDF d'une fiche de paie via Thymeleaf + openhtmltopdf,
 * en réutilisant l'infrastructure du PDF des comptes-rendus.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PayrollPdfServiceImpl implements PayrollPdfService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final String[] MOIS = {
            "", "Janvier", "Février", "Mars", "Avril", "Mai", "Juin",
            "Juillet", "Août", "Septembre", "Octobre", "Novembre", "Décembre"
    };

    private final EmployeePayrollRepository payrollRepository;
    private final SettingAppRepository settingAppRepository;
    private final SpringTemplateEngine templateEngine;

    @Override
    @Transactional(readOnly = true)
    public byte[] generatePdf(UUID employeeId, UUID payrollId) {
        EmployeePayroll payroll = payrollRepository.findByIdAndEmployeeId(payrollId, employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Fiche de paie", payrollId));
        Employee emp = payroll.getEmployee();

        Context ctx = new Context();
        // En-tête / pied de page société (settings partagés avec le PDF des rapports)
        ctx.setVariable("entete", settingAppRepository.findByKey("entete")
                .map(SettingApp::getValue).orElse(""));
        ctx.setVariable("footer", settingAppRepository.findByKey("report_footer")
                .map(SettingApp::getValue).filter(v -> !v.isBlank())
                .orElse(SettingApp.DEFAULT_REPORT_FOOTER));

        // Employé
        ctx.setVariable("employeeName",
                (safe(emp.getFirstName()) + " " + safe(emp.getLastName())).trim());
        ctx.setVariable("position", safe(emp.getPosition()));
        ctx.setVariable("phone", safe(emp.getPhone()));
        ctx.setVariable("email", safe(emp.getEmail()));
        ctx.setVariable("hireDate",
                emp.getHireDate() != null ? emp.getHireDate().format(DATE_FMT) : "—");

        // Période + montants
        ctx.setVariable("period", moisLabel(payroll.getMonth()) + " " + payroll.getYear());
        ctx.setVariable("grossSalary", money(payroll.getGrossSalary()));
        ctx.setVariable("deductions", money(payroll.getDeductions()));
        ctx.setVariable("netSalary", money(payroll.getNetSalary()));
        ctx.setVariable("paidAt",
                payroll.getPaidAt() != null ? payroll.getPaidAt().format(DATE_FMT) : "Non payé");

        // Métadonnées document
        ctx.setVariable("reference",
                "FP-" + payroll.getId().toString().substring(0, 8).toUpperCase(Locale.ROOT));
        ctx.setVariable("generatedAt", LocalDate.now().format(DATE_FMT));

        String html = com.labo.anapath.common.pdf.PdfHtmlUtil.toXhtml(
                templateEngine.process("pdf/fiche-paie", ctx));

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.withHtmlContent(html, null);
            builder.toStream(outputStream);
            builder.run();
            return outputStream.toByteArray();
        } catch (Exception e) {
            log.error("Erreur génération PDF fiche de paie {}", payrollId, e);
            throw new InvalidOperationException(
                    "Erreur lors de la génération du PDF: " + e.getMessage());
        }
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static String moisLabel(int month) {
        return (month >= 1 && month <= 12) ? MOIS[month] : String.valueOf(month);
    }

    private static String money(BigDecimal value) {
        if (value == null) return "0 FCFA";
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.FRANCE);
        nf.setMaximumFractionDigits(0);
        return nf.format(value) + " FCFA";
    }
}
