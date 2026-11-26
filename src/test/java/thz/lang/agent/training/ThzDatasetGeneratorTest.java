package thz.lang.agent.training;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Testes do ThzDatasetGenerator para Fine-Tuning LoRA")
class ThzDatasetGeneratorTest {

    @Test
    @DisplayName("Deve gerar lista de exemplos de treino com categorias variadas")
    void testGerarExemplosPadrao() {
        var exemplos = ThzDatasetGenerator.gerarExemplosPadrao();

        assertNotNull(exemplos);
        assertFalse(exemplos.isEmpty());
        assertTrue(exemplos.size() >= 5);

        boolean temModerno = exemplos.stream().anyMatch(e -> e.categoria().contains("modernas") || e.categoria().contains("loops"));
        boolean temFoundational = exemplos.stream().anyMatch(e -> e.categoria().contains("foundational"));
        boolean temCobol = exemplos.stream().anyMatch(e -> e.categoria().contains("cobol"));

        assertTrue(temModerno, "Deve conter exemplos de sintaxe moderna");
        assertTrue(temFoundational, "Deve conter exemplos de sintaxe foundational");
        assertTrue(temCobol, "Deve conter exemplos de interoperabilidade COBOL");
    }

    @Test
    @DisplayName("Deve exportar dataset em formato JSONL ChatML compatível com HuggingFace")
    void testExportarJsonl(@TempDir Path tempDir) throws IOException {
        Path jsonl = tempDir.resolve("thz_dataset.jsonl");
        var exemplos = ThzDatasetGenerator.gerarExemplosPadrao();

        int total = ThzDatasetGenerator.exportarJsonl(exemplos, jsonl);
        assertEquals(exemplos.size(), total);
        assertTrue(Files.exists(jsonl));

        List<String> linhas = Files.readAllLines(jsonl);
        assertEquals(total, linhas.size());

        for (String linha : linhas) {
            assertTrue(linha.startsWith("{\"messages\":["));
            assertTrue(linha.contains("\"role\":\"system\""));
            assertTrue(linha.contains("\"role\":\"user\""));
            assertTrue(linha.contains("\"role\":\"assistant\""));
            assertTrue(linha.contains("```thz"));
        }
    }
}
