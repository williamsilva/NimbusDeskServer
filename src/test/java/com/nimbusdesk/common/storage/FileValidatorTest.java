package com.nimbusdesk.common.storage;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

class FileValidatorTest {

  private StorageProperties props;
  private FileValidator validator;

  @BeforeEach
  void setUp() {
    props = new StorageProperties();
    props.setMaxUploadSizeMb(1);
    validator = new FileValidator(props);
  }

  @Test
  void acceptsAllowedContentTypeWithinSizeLimitAndMatchingSignature() {
    var file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", jpegBytes(1024));
    assertThatCode(() -> validator.validate(file)).doesNotThrowAnyException();
  }

  @Test
  void rejectsDisallowedContentType() {
    var file = new MockMultipartFile("file", "video.mp4", "video/mp4", new byte[1024]);
    assertThatThrownBy(() -> validator.validate(file)).isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void rejectsFileAboveMaxSize() {
    var file = new MockMultipartFile("file", "doc.pdf", "application/pdf", new byte[2 * 1024 * 1024]);
    assertThatThrownBy(() -> validator.validate(file)).isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void rejectsContentThatDoesNotMatchDeclaredContentType() {
    // Cliente malicioso declara "image/jpeg", mas o conteúdo real não tem a assinatura JPEG.
    var file = new MockMultipartFile("file", "fake.jpg", "image/jpeg", "<html>não é jpeg</html>".getBytes());
    assertThatThrownBy(() -> validator.validate(file)).isInstanceOf(ResponseStatusException.class);
  }

  /** Achado real 2026-09-16: anexo .zip num comentário de chamado era rejeitado com "Unsupported
   *  content type: application/x-zip-compressed" (variante que o Windows costuma declarar pro
   *  mesmo arquivo .zip - só "application/zip" estava na whitelist). */
  @Test
  void acceptsZipWithRealSignature() {
    var file = new MockMultipartFile("file", "anexo.zip", "application/zip", zipBytes());
    assertThatCode(() -> validator.validate(file)).doesNotThrowAnyException();
  }

  @Test
  void acceptsWindowsZipContentType() {
    var file = new MockMultipartFile("file", "anexo.zip", "application/x-zip-compressed", zipBytes());
    assertThatCode(() -> validator.validate(file)).doesNotThrowAnyException();
  }

  @Test
  void acceptsEmptyZipSignature() {
    byte[] emptyZip = { 0x50, 0x4B, 0x05, 0x06, 0, 0, 0, 0, 0, 0, 0, 0 };
    var file = new MockMultipartFile("file", "vazio.zip", "application/zip", emptyZip);
    assertThatCode(() -> validator.validate(file)).doesNotThrowAnyException();
  }

  @Test
  void rejectsContentThatDoesNotMatchDeclaredZipSignature() {
    var file = new MockMultipartFile("file", "fake.zip", "application/zip", "<html>não é zip</html>".getBytes());
    assertThatThrownBy(() -> validator.validate(file)).isInstanceOf(ResponseStatusException.class);
  }

  private byte[] zipBytes() {
    return new byte[] { 0x50, 0x4B, 0x03, 0x04, 0, 0, 0, 0, 0, 0, 0, 0 };
  }

  private byte[] jpegBytes(int size) {
    byte[] bytes = new byte[size];
    bytes[0] = (byte) 0xFF;
    bytes[1] = (byte) 0xD8;
    bytes[2] = (byte) 0xFF;
    return bytes;
  }
}
