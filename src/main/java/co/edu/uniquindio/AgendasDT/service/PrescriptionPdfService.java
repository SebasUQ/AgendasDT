package co.edu.uniquindio.AgendasDT.service;

import co.edu.uniquindio.AgendasDT.dto.DoctorDtos.PrescriptionResponse;
import co.edu.uniquindio.AgendasDT.exception.ClinicalException;
import java.io.*;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.*;
import org.springframework.stereotype.Service;
import static org.springframework.http.HttpStatus.*;

@Service
public class PrescriptionPdfService {
    public byte[] render(PrescriptionResponse prescription) {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream();
             InputStream fontData = PDDocument.class.getResourceAsStream("/org/apache/pdfbox/resources/ttf/LiberationSans-Regular.ttf")) {
            if (fontData == null) { throw new IOException("No se encontró la tipografía del documento."); }
            PDType0Font font = PDType0Font.load(document,fontData);
            try (Layout layout = new Layout(document,font)) {
                layout.paragraph("PRESCRIPCIÓN ODONTOLÓGICA");
                layout.paragraph("Documento: " + prescription.id());
                if (prescription.supersededById() != null) {
                    layout.paragraph("RECETA SUSTITUIDA. Consultar la prescripción: " + prescription.supersededById());
                }
                layout.paragraph("Fecha: " + prescription.issuedAt().atZone(ZoneId.of("America/Bogota"))
                        .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm 'America/Bogota'")));
                layout.paragraph("Paciente: " + prescription.patientName() + " | Identificación: " + prescription.patientId());
                layout.paragraph("Odontólogo: " + prescription.doctorName());
                layout.paragraph("Registro profesional: " + prescription.professionalRegistration());
                if (prescription.supersedesId() != null) {
                    layout.paragraph("Corrige la receta: " + prescription.supersedesId());
                    layout.paragraph("Motivo: " + prescription.correctionReason());
                }
                layout.paragraph("");
                int index = 0;
                for (var item : prescription.items()) {
                    layout.paragraph(++index + ". " + item.medicine());
                    layout.paragraph("Dosis: " + item.dose());
                    layout.paragraph("Frecuencia: " + item.frequency() + " | Duración: " + item.duration());
                    if (item.instructions() != null && !item.instructions().isBlank()) {
                        layout.paragraph("Indicaciones: " + item.instructions());
                    }
                    layout.paragraph("");
                }
                layout.paragraph("Firma del profesional: ___________________________________");
                layout.paragraph("Evolución de origen: " + prescription.recordId());
            }
            document.save(out);
            return out.toByteArray();
        } catch (IllegalArgumentException ex) {
            throw new ClinicalException(BAD_REQUEST, "El texto contiene símbolos no disponibles en la tipografía del PDF.");
        } catch (IOException ex) {
            throw new ClinicalException(INTERNAL_SERVER_ERROR, "No fue posible generar la prescripción PDF.");
        }
    }

    /** Wrap by actual font width, including long words, and paginate before crossing the bottom margin. */
    private static final class Layout implements AutoCloseable {
        private final PDDocument document;
        private final PDFont font;
        private PDPageContentStream stream;
        private float y;
        private static final float SIZE = 10.5f, WIDTH = 499;
        private int page;
        Layout(PDDocument document, PDFont font) throws IOException { this.document = document; this.font = font; newPage(); }
        void newPage() throws IOException {
            if (stream != null) { stream.close(); }
            PDPage sheet = new PDPage(PDRectangle.A4); document.addPage(sheet);
            stream = new PDPageContentStream(document,sheet);
            stream.setNonStrokingColor(0.04f,0.35f,0.45f);
            text("Doc-Tooth",48,800,20);
            stream.setStrokingColor(0.04f,0.35f,0.45f);
            stream.moveTo(48,786); stream.lineTo(547,786); stream.stroke();
            stream.setNonStrokingColor(0.2f,0.2f,0.2f);
            text("Historia clínica | Página " + ++page,48,30,8);
            y = 764;
        }
        void paragraph(String value) throws IOException {
            for (String paragraph : value.replace('\t',' ').split("\\R",-1)) {
                StringBuilder line = new StringBuilder();
                for (String word : paragraph.split(" +")) {
                    if (word.isEmpty()) { continue; }
                    String candidate = line.isEmpty() ? word : line + " " + word;
                    if (width(candidate) <= WIDTH) { line.setLength(0); line.append(candidate); continue; }
                    if (!line.isEmpty()) { line(line.toString()); line.setLength(0); }
                    for (int cp : word.codePoints().toArray()) {
                        String character = new String(Character.toChars(cp));
                        if (width(line + character) > WIDTH) { line(line.toString()); line.setLength(0); }
                        line.append(character);
                    }
                }
                line(line.toString());
                y -= 3;
            }
        }
        private float width(String value) throws IOException { return font.getStringWidth(value) * SIZE / 1000; }
        private void line(String value) throws IOException {
            if (y < 60) { newPage(); }
            text(value,48,y,SIZE); y -= 15;
        }
        private void text(String value, float x, float baseline, float size) throws IOException {
            stream.beginText(); stream.setFont(font,size); stream.newLineAtOffset(x,baseline); stream.showText(value); stream.endText();
        }
        @Override public void close() throws IOException { if (stream != null) { stream.close(); } }
    }
}
