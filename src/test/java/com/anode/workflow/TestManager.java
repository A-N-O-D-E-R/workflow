package com.anode.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.anode.tool.StringUtils;
import com.anode.tool.document.Document;
import com.anode.workflow.mapper.AbstractMapper;
import com.anode.workflow.test_singular.TestWorkflowService;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Map;

public class TestManager {

    private static PrintStream previousConsole = null;
    private static ByteArrayOutputStream newConsole = null;
    private static boolean isLoaded = false;

    public static void init(
            PrintStream previousConsole,
            ByteArrayOutputStream newConsole,
            int maxThreads,
            int idleTimeout) {
        if (TestManager.previousConsole == null) {
            TestManager.previousConsole = previousConsole;
        }

        if (TestManager.newConsole == null) {
            TestManager.newConsole = newConsole;
            System.setOut(new PrintStream(newConsole));
        }

        if (isLoaded == false) {
            isLoaded = true;
            AbstractMapper.loadModel();
            WorkflowService.init(maxThreads, idleTimeout, "-");
            //    WorkflowService.instance().setWriteAuditLog(false);
            //    WorkflowService.instance().setWriteProcessInfoAfterEachStep(false);
        } else {
            WorkflowService.close();
            AbstractMapper.loadModel();
            WorkflowService.init(maxThreads, idleTimeout, "-");
            //    WorkflowService.instance().setWriteAuditLog(false);
            //    WorkflowService.instance().setWriteProcessInfoAfterEachStep(false);
        }
    }

    public static void reset() {
        TestManager.newConsole.reset();
    }

    public static void myAssertEquals1(
            boolean writeToConsole, String testCase, String resourcePath) {
        String s = newConsole.toString();
        String output = trimLines(s);
        String expected = StringUtils.getResourceAsString(TestWorkflowService.class, resourcePath);
        expected = trimLines(expected);
        assertEquals(expected, output);
        previousConsole.println(
                "*********************** Test case successful, test case name -> " + testCase);
        if (writeToConsole == true) {
            previousConsole.println();
            previousConsole.println(s);
            previousConsole.println();
            previousConsole.println();
        }
        previousConsole.flush();
    }

    public static void myAssertEqualsTodo(
            boolean writeToConsole, String testCase, String resourcePath) {
        String s = newConsole.toString();
        previousConsole.println(
                "*********************** Test case run with no assertion, test case name -> "
                        + testCase);
        if (writeToConsole == true) {
            previousConsole.println();
            previousConsole.println(s);
            previousConsole.println();
            previousConsole.println();
        }
        previousConsole.flush();
    }

    public static void myAssertEquals2(
            boolean writeToConsole, String testCase, String resourcePath) {
        String s = newConsole.toString();
        String output = getSortedWithoutCrLf(s);
        String expected = StringUtils.getResourceAsString(TestWorkflowService.class, resourcePath);
        expected = getSortedWithoutCrLf(expected);
        assertEquals(expected, output);
        previousConsole.println(
                "*********************** Test case successful, test case name -> " + testCase);
        if (writeToConsole == true) {
            previousConsole.println();
            previousConsole.println(s);
            previousConsole.println();
            previousConsole.println();
        }
        previousConsole.flush();
    }

    public static void deleteFiles(String dirPath) {
        try {
            Files.walk(Paths.get(dirPath))
                    .filter(Files::isRegularFile)
                    .map(Path::toFile)
                    .forEach(File::delete);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void writeFiles(
            boolean writeFiles, String dirPath, Map<Serializable, Object> documents) {
        if (writeFiles == false) {
            return;
        }

        File directory = new File(dirPath);
        if (directory.exists() == false) {
            directory.mkdirs();
        } else {
            try {
                Files.walk(Paths.get(dirPath))
                        .filter(Files::isRegularFile)
                        .map(Path::toFile)
                        .forEach(File::delete);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        for (Serializable key : documents.keySet()) {
            Object d = documents.get(key);
            FileWriter fw = null;
            try {
                String fileName = directory.getCanonicalPath() + "/" + key + ".json";
                fw = new FileWriter(fileName);
                fw.write(((Document) d).getPrettyPrintJson());
                fw.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static String getSortedWithoutCrLf(String s) {
        s = s.replaceAll("\r\n", "\n");
        String[] lines = s.split("\n");
        Arrays.sort(lines);
        s = "";
        for (String line : lines) {
            line = line.trim();
            s = s + line;
        }
        return s;
    }

    public static String trimLines(String s) {
        String[] lines = s.split("\n");
        s = "";
        for (String line : lines) {
            line = line.trim();
            s = s + line + "\n";
        }
        return s;
    }
}
