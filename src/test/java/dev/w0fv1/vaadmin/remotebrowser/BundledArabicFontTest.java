package dev.w0fv1.vaadmin.remotebrowser;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.awt.Font;
import java.util.zip.ZipFile;
import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "BROWSER_FONT_RUNTIME", matches = ".+")
class BundledArabicFontTest {
    @Test
    void linuxBundleCoversExampleDomainArabicParagraph() throws Exception {
        String text = "هذا النطاق مُخصص للاستخدام في أمثلة التوثيق دون الحاجة إلى إذن. هذه ليست خدمة، يُرجى تجنب الاعتماد عليها لأغراض الاختبار والمراقبة.";
        try (var archive = new ZipFile(System.getenv("BROWSER_FONT_RUNTIME"))) {
            var entry = archive.stream().filter(file -> file.getName().endsWith("/NotoSansArabic-Regular.ttf")).findFirst();
            assertThat(entry).as("bundled Arabic font").isPresent();
            try (var input = archive.getInputStream(entry.orElseThrow())) {
                Font font = Font.createFont(Font.TRUETYPE_FONT, input);
                assertThat(font.canDisplayUpTo(text)).as("first unsupported character").isEqualTo(-1);
            }
        }
    }
}
