package com.toscaruntime.util;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.commons.lang.StringUtils;

public class ZipDirWalker extends SimpleFileVisitor<Path> {

    private Path inputPath;

    private ZipOutputStream zipOutputStream;

    private String outputPrefix;

    public ZipDirWalker(Path inputPath, ZipOutputStream zipOutputStream, String outputPrefix) {
        this.inputPath = inputPath;
        this.zipOutputStream = zipOutputStream;
        this.outputPrefix = outputPrefix;
    }

    private String getPrefix() {
        return StringUtils.isNotBlank(this.outputPrefix) ? this.outputPrefix + "/" : "";
    }

    @Override
    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
        if (!dir.equals(inputPath)) {
            zipOutputStream.putNextEntry(new ZipEntry(getPrefix() + FileUtil.getChildEntryRelativePath(inputPath, dir, true)));
        }
        return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        FileUtil.putZipEntry(zipOutputStream, new ZipEntry(getPrefix() + FileUtil.getChildEntryRelativePath(inputPath, file, true)), file);
        return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
        try {
            zipOutputStream.close();
        } catch (IOException ignored) {
        }
        throw exc;
    }
}
