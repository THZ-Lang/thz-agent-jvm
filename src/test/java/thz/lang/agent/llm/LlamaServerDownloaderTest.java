package thz.lang.agent.llm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class LlamaServerDownloaderTest {

    @Test
    @DisplayName("Deve retornar caminho de diretório sob user.home")
    void testLlamaDir() {
        Path dir = LlamaServerDownloader.getLlamaDir();
        assertNotNull(dir);
        assertTrue(dir.toString().contains("llama.cpp"));
        assertTrue(dir.startsWith(Path.of(System.getProperty("user.home"))));
    }

    @Test
    @DisplayName("Deve retornar nome de executável adequado ao sistema operacional")
    void testLlamaServerPath() {
        Path serverPath = LlamaServerDownloader.getLlamaServerPath();
        assertNotNull(serverPath);
        String name = serverPath.getFileName().toString();
        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
        if (isWindows) {
            assertEquals("llama-server.exe", name);
        } else {
            assertEquals("llama-server", name);
        }
    }
}
