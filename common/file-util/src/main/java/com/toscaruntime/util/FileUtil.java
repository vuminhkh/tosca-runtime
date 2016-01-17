package com.toscaruntime.util;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.commons.compress.utils.Charsets;
import org.apache.commons.compress.utils.IOUtils;
import org.apache.commons.lang.StringUtils;

public final class FileUtil {

    private FileUtil() {
    }

    static void putZipEntry(ZipOutputStream zipOutputStream, ZipEntry zipEntry, Path file) throws IOException {
        zipOutputStream.putNextEntry(zipEntry);
        try (InputStream input = new BufferedInputStream(Files.newInputStream(file))) {
            IOUtils.copy(input, zipOutputStream);
            zipOutputStream.closeEntry();
        }
    }

    public static String getChildEntryRelativePath(Path base, Path child, boolean convertToLinuxPath) {
        String path = base.toUri().relativize(child.toUri()).getPath();
        if (convertToLinuxPath && !"/".equals(base.getFileSystem().getSeparator())) {
            return path.replace(base.getFileSystem().getSeparator(), "/");
        } else {
            return path;
        }
    }

    /**
     * Recursively zip file and directory
     *
     * @param inputPath  file path can be directory
     * @param outputPath where to put the zip
     * @throws IOException when IO error happened
     */
    public static void zip(Path inputPath, Path outputPath) throws IOException {
        zip(inputPath, "", outputPath);
    }

    public static void zip(Path inputPath, String outputPrefix, Path outputPath) throws IOException {
        if (!Files.exists(inputPath)) {
            throw new FileNotFoundException("File not found " + inputPath);
        }
        touch(outputPath);
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(outputPath)))) {
            if (!Files.isDirectory(inputPath)) {
                putZipEntry(zipOutputStream, new ZipEntry(outputPrefix + "/" + inputPath.getFileName().toString()), inputPath);
            } else {
                Files.walkFileTree(inputPath, new ZipDirWalker(inputPath, zipOutputStream, outputPrefix));
            }
            zipOutputStream.flush();
        }
    }

    /**
     * Unzip a zip file to a destination folder.
     *
     * @param zipFile     The zip file to unzip.
     * @param destination The destination folder in which to save the file.
     * @throws IOException In case something fails.
     */
    public static void unzip(final Path zipFile, final Path destination) throws IOException {
        try (FileSystem zipFS = FileSystems.newFileSystem(zipFile, null)) {
            final Path root = zipFS.getPath("/");
            copy(root, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static String relativizePath(Path root, Path child) {
        String childPath = child.toAbsolutePath().toString();
        String rootPath = root.toAbsolutePath().toString();
        if (childPath.equals(rootPath)) {
            return "";
        }
        int indexOfRootInChild = childPath.indexOf(rootPath);
        if (indexOfRootInChild != 0) {
            throw new IllegalArgumentException("Child path " + childPath + "is not beginning with root path " + rootPath);
        }
        String relativizedPath = childPath.substring(rootPath.length(), childPath.length());
        while (relativizedPath.startsWith(root.getFileSystem().getSeparator())) {
            relativizedPath = relativizedPath.substring(1);
        }
        return relativizedPath;
    }

    /**
     * Copy file or directory. In case of directory, the copy is recursive
     *
     * @param source      source directory or file
     * @param destination destination directory or file
     * @param options     copy options
     * @throws IOException
     */
    public static void copy(final Path source, final Path destination, final CopyOption... options) throws IOException {
        if (Files.isRegularFile(source)) {
            // Simple file copy
            if (Files.notExists(destination)) {
                Files.createDirectories(destination.getParent());
            }
            Files.copy(source, destination, options);
            return;
        }

        // Directories copy
        if (Files.notExists(destination)) {
            Files.createDirectories(destination);
        }

        Files.walkFileTree(source, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                String fileRelativePath = relativizePath(source, file);
                Path destFile = destination.resolve(fileRelativePath);
                Files.copy(file, destFile, options);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                String dirRelativePath = relativizePath(source, dir);
                Path destDir = destination.resolve(dirRelativePath);
                Files.createDirectories(destDir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static class EraserWalker extends SimpleFileVisitor<Path> {
        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            Files.delete(file);
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
            if (exc == null) {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
            throw exc;
        }
    }

    /**
     * Recursively delete file and directory
     *
     * @param deletePath file path can be directory
     * @throws IOException when IO error happened
     */
    public static void delete(Path deletePath) throws IOException {
        if (!Files.exists(deletePath)) {
            return;
        }
        if (!Files.isDirectory(deletePath)) {
            Files.delete(deletePath);
            return;
        }
        Files.walkFileTree(deletePath, new EraserWalker());
    }

    /**
     * Read all files bytes and create a string.
     *
     * @param path    The file's path.
     * @param charset The charset to use to convert the bytes to string.
     * @return A string from the file content.
     * @throws IOException In case the file cannot be read.
     */
    public static String readTextFile(Path path, Charset charset) throws IOException {
        return new String(Files.readAllBytes(path), charset);
    }

    /**
     * Read all files bytes and create a string using UTF_8 charset.
     *
     * @param path The file's path.
     * @return A string from the file content.
     * @throws IOException In case the file cannot be read.
     */
    public static String readTextFile(Path path) throws IOException {
        return readTextFile(path, Charsets.UTF_8);
    }

    /**
     * Write given text to a file
     *
     * @param text       the text to write
     * @param outputFile the output
     * @return the path
     * @throws IOException
     */
    public static Path writeTextFile(String text, Path outputFile) throws IOException {
        Files.createDirectories(outputFile.getParent());
        return Files
                .write(outputFile, text.getBytes(Charsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
    }

    /**
     * Create an empty file at the given path
     *
     * @param path to create file
     * @throws IOException
     */
    public static boolean touch(Path path) throws IOException {
        Path parentDir = path.getParent();
        if (!Files.exists(parentDir)) {
            Files.createDirectories(parentDir);
            return true;
        }
        if (!Files.exists(path)) {
            Files.createFile(path);
            return true;
        }
        return false;
    }

    /**
     * List all files with extension
     *
     * @param path       the folder path
     * @param extensions the extension without '.'
     * @return list of all files in the folder that have the extension
     * @throws IOException
     */
    public static List<Path> listFiles(final Path path, final String... extensions) throws IOException {
        final List<Path> allFiles = new ArrayList<>();
        Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (extensions.length > 0) {
                    for (String extension : extensions) {
                        if (file.getFileName().toString().endsWith(extension)) {
                            allFiles.add(file);
                            break;
                        }
                    }
                } else {
                    allFiles.add(file);
                }
                return super.visitFile(file, attrs);
            }
        });
        return allFiles;
    }

    /**
     * List all children directories of a path
     *
     * @param path           path to list directories
     * @param dirNameFilters the dir name must contain one of those filter string (case ignored)
     * @return the list of path of all directories
     * @throws IOException
     */
    public static List<Path> listChildrenDirectories(final Path path, String... dirNameFilters) throws IOException {
        final List<Path> allDirs = new ArrayList<>();
        try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(path)) {
            for (Path child : directoryStream) {
                if (Files.isDirectory(child)) {
                    if (dirNameFilters.length > 0) {
                        for (String filter : dirNameFilters) {
                            if (StringUtils.containsIgnoreCase(child.getFileName().toString(), filter)) {
                                allDirs.add(child);
                            }
                        }
                    } else {
                        allDirs.add(child);
                    }
                }
            }
        }
        return allDirs;
    }

    /**
     * List files and directories of a parent directory
     *
     * @param path the directory path
     * @return the list of all file and nested directories inside this directory
     * @throws IOException
     */
    public static List<String> ls(final Path path) throws IOException {
        if (!Files.isDirectory(path)) {
            throw new IOException("Not a directory " + path);
        }
        final List<String> allFiles = new ArrayList<>();
        try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(path)) {
            for (Path child : directoryStream) {
                if (Files.isDirectory(child)) {
                    allFiles.add(child + "/");
                } else {
                    allFiles.add(child.toString());
                }
            }
        }
        allFiles.add(".");
        allFiles.add("..");
        return allFiles;
    }

    /**
     * Create a zip file system and returns back the root
     *
     * @param path the path to the file
     * @return the root of the new file system
     * @throws IOException
     */
    public static Path createZipFileSystem(Path path) throws IOException {
        Map<String, String> env = new HashMap<>();
        if (!Files.exists(path)) {
            env.put("create", "true");
        }
        URI uri = URI.create("jar:file:" + path.toUri().getPath());
        FileSystem fileSystem = FileSystems.newFileSystem(uri, env, null);
        return fileSystem.getPath("/");
    }
}
