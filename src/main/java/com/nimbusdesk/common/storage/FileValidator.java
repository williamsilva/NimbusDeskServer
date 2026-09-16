package com.nimbusdesk.common.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * Portado de com.nimbusflow.common.storage.FileValidator - valida tipo MIME e tamanho máximo de
 * anexo enviado, antes de aceitar. PDF/Office/texto (tipos novos, ver StorageProperties) caem no
 * {@code default -> true} do switch abaixo (aceitos pelo whitelist, sem checagem extra de
 * assinatura binária) - mesmo comportamento já previsto e documentado no código original pra
 * qualquer tipo sem assinatura conhecida mapeada. Zip tem checagem própria (ver
 * {@link #matchesDeclaredContentType}) - mesmo hardening já aplicado no NimbusFlowServer.
 */
@Component
@RequiredArgsConstructor
public class FileValidator {

  // Bytes suficientes pra checar a assinatura mais "funda" que validamos (WEBP: offset 8-12).
  private static final int HEADER_SNIFF_BYTES = 16;

  private static final byte[] JPEG_SIGNATURE = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF };
  private static final byte[] PNG_SIGNATURE = { (byte) 0x89, 0x50, 0x4E, 0x47 };
  private static final byte[] RIFF_SIGNATURE = "RIFF".getBytes(StandardCharsets.US_ASCII);
  private static final byte[] WEBP_SIGNATURE = "WEBP".getBytes(StandardCharsets.US_ASCII);

  /** ZIP (local file header) - assinatura da grande maioria dos .zip de verdade. Um .zip
   *  totalmente vazio (só End of Central Directory, sem nenhuma entrada) começa com
   *  EMPTY_ZIP_SIGNATURE em vez desta - aceito também abaixo pra não rejeitar esse caso de borda
   *  legítimo (mesmo hardening do NimbusFlowServer). */
  private static final byte[] ZIP_SIGNATURE = { 0x50, 0x4B, 0x03, 0x04 };
  private static final byte[] EMPTY_ZIP_SIGNATURE = { 0x50, 0x4B, 0x05, 0x06 };

  private final StorageProperties props;

  public void validate(MultipartFile file) {
    String contentType = file.getContentType();
    if (contentType == null || !props.getAllowedContentTypes().contains(contentType)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported content type: " + contentType);
    }

    long maxBytes = (long) props.getMaxUploadSizeMb() * 1024 * 1024;
    if (file.getSize() > maxBytes) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File exceeds max size of " + props.getMaxUploadSizeMb() + "MB");
    }

    if (!matchesDeclaredContentType(file, contentType)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
          "File content does not match the declared content type: " + contentType);
    }
  }

  /**
   * Confere a assinatura binária real (magic bytes) do início do arquivo antes de aceitar, pros
   * tipos de imagem e zip suportados hoje (mesmo hardening do NimbusFlowServer). PDF/Office/texto
   * (tipos novos deste app, sem vídeo) caem no {@code default -> true} - aceitos pelo whitelist
   * acima sem checagem extra, mesmo racional documentado no arquivo original.
   */
  private boolean matchesDeclaredContentType(MultipartFile file, String contentType) {
    byte[] header = readHeader(file);
    return switch (contentType) {
      case "image/jpeg" -> startsWith(header, JPEG_SIGNATURE);
      case "image/png" -> startsWith(header, PNG_SIGNATURE);
      case "image/webp" -> startsWith(header, RIFF_SIGNATURE) && containsAt(header, 8, WEBP_SIGNATURE);
      case "application/zip", "application/x-zip-compressed" ->
          startsWith(header, ZIP_SIGNATURE) || startsWith(header, EMPTY_ZIP_SIGNATURE);
      default -> true;
    };
  }

  private byte[] readHeader(MultipartFile file) {
    try (InputStream in = file.getInputStream()) {
      return in.readNBytes(HEADER_SNIFF_BYTES);
    } catch (IOException e) {
      throw new UncheckedIOException("Could not read uploaded file header", e);
    }
  }

  private boolean startsWith(byte[] data, byte[] signature) {
    return containsAt(data, 0, signature);
  }

  private boolean containsAt(byte[] data, int offset, byte[] signature) {
    if (data.length < offset + signature.length) {
      return false;
    }
    for (int i = 0; i < signature.length; i++) {
      if (data[offset + i] != signature[i]) {
        return false;
      }
    }
    return true;
  }
}
