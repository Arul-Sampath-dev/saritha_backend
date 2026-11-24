package com.saritha.fashions.modules.core.pdf;

import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.apache.fontbox.FontBoxFont;
import org.apache.fontbox.ttf.TTFParser;
import org.apache.fontbox.ttf.TrueTypeFont;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.font.CIDFontMapping;
import org.apache.pdfbox.pdmodel.font.FontMapper;
import org.apache.pdfbox.pdmodel.font.FontMapping;
import org.apache.pdfbox.pdmodel.font.FontMappers;
import org.apache.pdfbox.pdmodel.font.PDCIDSystemInfo;
import org.apache.pdfbox.pdmodel.font.PDFontDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders the PDF documents (invoice, receipt, payment receipt) inside the backend: a Thymeleaf HTML template from
 * src/main/resources/pdf/templates is filled and turned into a PDF by OpenHTMLtoPDF (Apache PDFBox).
 * No external converter, no LibreOffice: a one-page document takes ~0.1–0.3 s and a few MB of memory.
 *
 * Fonts: Noto Sans (bundled, SIL OFL, pdf/fonts) so the ₹ sign and Indian names print the same on every server.
 * The logo is passed to every template as the data URI {@code logo}.
 *
 * PDFBox would scan every font installed on the server the first time a PDF is made (seconds, plus a font cache
 * file). All text uses the bundled Noto Sans, so the font lookup is replaced by one that answers Noto Sans at once.
 *
 * Templates must be well-formed XHTML (closed tags, &#160; instead of &nbsp;): the PDF renderer parses them as XML.
 */
@Component
public class HtmlPdfRenderer {

    static final String FONT_FAMILY = "Noto Sans";

    private static final Logger log = LoggerFactory.getLogger(HtmlPdfRenderer.class);

    private final TemplateEngine templates;
    private final byte[] regular;
    private final byte[] bold;
    private final byte[] italic;
    private final String logo;

    public HtmlPdfRenderer() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("pdf/templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        resolver.setCacheable(true);
        this.templates = new TemplateEngine();
        this.templates.setTemplateResolver(resolver);

        this.regular = read("pdf/fonts/NotoSans-Regular.ttf");
        this.bold = read("pdf/fonts/NotoSans-Bold.ttf");
        this.italic = read("pdf/fonts/NotoSans-Italic.ttf");
        this.logo = "data:image/png;base64," + Base64.getEncoder().encodeToString(read("report/logo.png"));
        FontMappers.set(new BundledFontMapper(regular));
    }

    /**
     * @param template file name in pdf/templates without ".html", e.g. "invoice"
     * @param model    template variables ({@code logo} is added)
     * @throws PdfGenerationException if the template or the PDF cannot be produced
     */
    public byte[] render(String template, Map<String, Object> model) {
        String html;
        try {
            Context context = new Context();
            context.setVariables(model);
            context.setVariable("logo", logo);
            html = templates.process(template, context);
        } catch (RuntimeException exception) {
            throw new PdfGenerationException("Could not fill the " + template + " template", exception);
        }
        return toPdf(html, template);
    }

    /**
     * The first PDF after a start is slow (~2–5 s: the template engine and the PDF library load their classes);
     * later ones take ~0.1–0.3 s. One throw-away payment receipt is rendered in the background right after startup
     * so no customer waits for that.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void warmUp() {
        Thread warmUp = new Thread(() -> {
            long start = System.nanoTime();
            try {
                Map<String, Object> model = new HashMap<>();
                model.put("v", SalesDocumentPdfService.printable(new HashMap<>()));
                model.put("items", List.of());
                model.put("payments", List.of());
                model.put("blankRows", List.of());
                model.put("showCgstSgst", true);
                model.put("showIgst", false);
                model.put("footerText", "warm-up");
                render("invoice", model);
                render("payment-receipt", model);
                log.info("PDF renderer ready ({} ms)", (System.nanoTime() - start) / 1_000_000);
            } catch (RuntimeException exception) {
                log.warn("PDF renderer warm-up failed: {}", exception.getMessage());
            }
        }, "pdf-warmup");
        warmUp.setDaemon(true);
        warmUp.start();
    }

    /** The filled HTML only (for tests / previews). */
    String html(String template, Map<String, Object> model) {
        Context context = new Context();
        context.setVariables(model);
        context.setVariable("logo", logo);
        return templates.process(template, context);
    }

    private byte[] toPdf(String html, String template) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream(64 * 1024)) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFont(() -> new ByteArrayInputStream(regular), FONT_FAMILY, 400, FontStyle.NORMAL, true);
            builder.useFont(() -> new ByteArrayInputStream(bold), FONT_FAMILY, 700, FontStyle.NORMAL, true);
            builder.useFont(() -> new ByteArrayInputStream(italic), FONT_FAMILY, 400, FontStyle.ITALIC, true);
            builder.withHtmlContent(html, null);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (Exception exception) {
            throw new PdfGenerationException("Could not render the " + template + " PDF", exception);
        }
    }

    /**
     * Font lookup without scanning the system fonts: every font PDFBox asks for (only its built-in fallbacks;
     * the documents themselves use the embedded Noto Sans) maps to Noto Sans.
     */
    static final class BundledFontMapper implements FontMapper {

        private final TrueTypeFont font;

        BundledFontMapper(byte[] ttf) {
            try {
                this.font = new TTFParser().parse(new RandomAccessReadBuffer(ttf));
            } catch (IOException exception) {
                throw new UncheckedIOException("Cannot read the bundled font", exception);
            }
        }

        @Override
        public FontMapping<TrueTypeFont> getTrueTypeFont(String baseFont, PDFontDescriptor descriptor) {
            return new FontMapping<>(font, true);
        }

        @Override
        public FontMapping<FontBoxFont> getFontBoxFont(String baseFont, PDFontDescriptor descriptor) {
            return new FontMapping<>(font, true);
        }

        @Override
        public CIDFontMapping getCIDFont(String baseFont, PDFontDescriptor descriptor, PDCIDSystemInfo cidSystemInfo) {
            return new CIDFontMapping(null, font, true);
        }
    }

    private static byte[] read(String path) {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return in.readAllBytes();
        } catch (IOException exception) {
            throw new UncheckedIOException("Missing PDF resource " + path, exception);
        }
    }
}
