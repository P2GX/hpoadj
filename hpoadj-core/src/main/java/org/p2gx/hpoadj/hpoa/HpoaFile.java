package org.p2gx.hpoadj.hpoa;

import org.monarchinitiative.phenol.ontology.data.TermId;
import org.p2gx.hpoadj.HpoadjException;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class HpoaFile {

    private final List<String> headerLines;
    private final List<HpoaAnnotationLine> annotationLines;

    public HpoaFile(List<String> headerLines, List<HpoaAnnotationLine> annotationLines) {
        this.headerLines = List.copyOf(headerLines);
        this.annotationLines = List.copyOf(annotationLines);
    }

    public static HpoaFile parse(Path path) {
        List<String> header = new ArrayList<>();
        List<HpoaAnnotationLine> annotations = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(path)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("#") || line.startsWith("database_id")) {
                    header.add(line);
                } else if (!line.isBlank()) {
                    annotations.add(HpoaAnnotationLine.of(line));
                }
            }
        } catch (IOException e) {
            throw new HpoadjException("Could not read HPOA file " + path, e);
        }
        return new HpoaFile(header, annotations);
    }

    public List<String> headerLines() {
        return headerLines;
    }

    public List<HpoaAnnotationLine> annotationLines() {
        return annotationLines;
    }

    public int annotationCount() {
        return annotationLines.size();
    }

    public List<HpoaAnnotationLine> linesCiting(TermId reference) {
        return annotationLines.stream()
                .filter(line -> line.cites(reference))
                .toList();
    }

    public long phenotypeLineCount(TermId diseaseId) {
        return annotationLines.stream()
                .filter(line -> line.diseaseId().equals(diseaseId))
                .filter(HpoaAnnotationLine::isPhenotypeAnnotation)
                .count();
    }

    public HpoaFile withHeaderLine(String headerLine) {
        List<String> header = new ArrayList<>(headerLines);
        header.add(Math.min(1, header.size()), headerLine);
        return new HpoaFile(header, annotationLines);
    }

    public void write(Path path) {
        try (BufferedWriter writer = Files.newBufferedWriter(path)) {
            for (String headerLine : headerLines) {
                writer.write(headerLine);
                writer.newLine();
            }
            for (HpoaAnnotationLine line : annotationLines) {
                writer.write(line.raw());
                writer.newLine();
            }
        } catch (IOException e) {
            throw new HpoadjException("Could not write HPOA file " + path, e);
        }
    }
}
