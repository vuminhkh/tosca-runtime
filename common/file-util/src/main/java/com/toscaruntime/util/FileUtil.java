package com.toscaruntime.util;

import org.apache.commons.compress.utils.Charsets;
import org.apache.commons.lang.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class FileUtil {

    private FileUtil() {
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
     * List all files with extension
     *
     * @param path       the folder path
     * @param extensions the extension without '.'
     * @return list of all files in the folder that have the extension
     * @throws IOException
     */
    public static List<Path> listFiles(final Path path, final String... extensions) throws IOException {
        return Files.list(path).filter(child -> {
            if (extensions.length > 0) {
                for (String extension : extensions) {
                    if (child.getFileName().toString().endsWith(extension)) {
                        return true;
                    }
                }
                return false;
            } else {
                return true;
            }
        }).collect(Collectors.toList());
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

    public static String getFileExtension(String path) {
        String fileName = Paths.get(path).getFileName().toString();
        int indexOfExtension = fileName.lastIndexOf('.');
        if (indexOfExtension > 0) {
            return fileName.substring(indexOfExtension + 1);
        } else {
            return null;
        }
    }
}
