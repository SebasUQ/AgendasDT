package co.edu.uniquindio.AgendasDT.service;

import co.edu.uniquindio.AgendasDT.dto.DoctorDtos.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PrescriptionPdfTests {
    @Test void producesUnicodePdfWithProfessionalAndPatientDetails() throws Exception {
        byte[] bytes = new PrescriptionPdfService().render(response(List.of(new MedicationRequest(
                "Texto farmacológico de prueba","Dosis registrada","Según prescripción","Duración registrada","Indicaciones: atención y revisión."))));
        try (var document = Loader.loadPDF(bytes)) {
            String text = new PDFTextStripper().getText(document);
            assertTrue(text.contains("María Pérez")); assertTrue(text.contains("Andrés Gómez"));
            assertTrue(text.contains("REG-123")); assertTrue(text.contains("Firma del profesional"));
            assertTrue(text.contains("Texto farmacológico de prueba"));
            Files.createDirectories(Path.of("target"));
            Files.write(Path.of("target/doctor-prescription-preview.pdf"),bytes);
            ImageIO.write(new PDFRenderer(document).renderImageWithDPI(0,96),"PNG",Path.of("target/doctor-prescription-preview.png").toFile());
        }
    }
    @Test void wrapsAndPaginatesLongPrescriptionsWithoutLosingContent() throws Exception {
        List<MedicationRequest> items = new ArrayList<>();
        for (int i=1;i<=30;i++) { items.add(new MedicationRequest("Línea "+i,"Dosis","Frecuencia","Duración",("Indicación extendida ").repeat(40))); }
        try (var document = Loader.loadPDF(new PrescriptionPdfService().render(response(items)))) {
            assertTrue(document.getNumberOfPages() > 3);
            String text = new PDFTextStripper().getText(document);
            assertTrue(text.contains("Línea 1")); assertTrue(text.contains("Línea 30"));
            assertTrue(text.contains("Firma del profesional"));
        }
    }
    private PrescriptionResponse response(List<MedicationRequest> items) {
        return new PrescriptionResponse(UUID.randomUUID(),UUID.randomUUID(),123L,"María Pérez","Andrés Gómez","REG-123",
                Instant.parse("2026-11-18T15:00:00Z"),null,null,items);
    }
}
