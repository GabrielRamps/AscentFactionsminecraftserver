package gg.ascent.api.config;

import java.util.List;
import org.jetbrains.annotations.Nullable;

/** The outcome of reloading every configuration file, one entry per file. */
public record ReloadReport(List<FileResult> files) {

  public ReloadReport {
    files = List.copyOf(files);
  }

  /** One file's outcome. {@code error} is null when the file loaded. */
  public record FileResult(String file, @Nullable String error) {
    public boolean ok() {
      return error == null;
    }
  }

  public boolean allOk() {
    return files.stream().allMatch(FileResult::ok);
  }

  public List<FileResult> failures() {
    return files.stream().filter(f -> !f.ok()).toList();
  }
}
