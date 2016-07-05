package com.airwatch.tool;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by manishk on 7/5/16.
 */
public class CsvReader {

    public static List<Device> createDevices() {
        List<List<String>> csvData = readRecords();
        return csvData.stream().map(attributes ->
                new Device().setEasDeviceId(attributes.get(0))
                        .setUserId(attributes.get(4))
                        .setEasDeviceId(attributes.get(2))
                        .setEasDeviceType(attributes.get(1)))
                .collect(Collectors.toList());
    }

    private static List<List<String>> readRecords() {
        try (BufferedReader reader = new BufferedReader(new FileReader("devices.csv"))) {
            return reader.lines()
                    .map(line -> Arrays.asList(line.split(",")))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
