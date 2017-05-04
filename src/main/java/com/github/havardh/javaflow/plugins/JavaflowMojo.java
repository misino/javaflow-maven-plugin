package com.github.havardh.javaflow.plugins;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.SuffixFileFilter;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import com.github.havardh.javaflow.Execution;
import com.github.havardh.javaflow.phases.filetransform.CommentAppendTransformer;
import com.github.havardh.javaflow.phases.filetransform.EslintDisableTransformer;
import com.github.havardh.javaflow.phases.parser.java.JavaParser;
import com.github.havardh.javaflow.phases.reader.FileReader;
import com.github.havardh.javaflow.phases.transform.InheritanceTransformer;
import com.github.havardh.javaflow.phases.transform.SortedTypeTransformer;
import com.github.havardh.javaflow.phases.verifier.MemberFieldsPresentVerifier;
import com.github.havardh.javaflow.phases.writer.flow.FlowWriter;
import com.github.havardh.javaflow.phases.writer.flow.converter.Converter;
import com.github.havardh.javaflow.phases.writer.flow.converter.JavaFlowConverter;
import com.github.havardh.javaflow.util.TypeMap;

@Mojo(name = "build")
public class JavaflowMojo extends AbstractMojo {

    @Parameter(property = "targetDirectory", defaultValue = "${basedir}/target")
    private String targetDirectory;

    @Parameter(property = "sourceDirectory", defaultValue = "${basedir}/src/main/java")
    private String sourceDirectory;

    @Parameter(property = "apis")
    private List<Api> apis;

    public void execute() throws MojoExecutionException {
      try {
        apis.forEach(this::run);
      } catch (Exception e) {
        throw new MojoExecutionException("Could not generate types", e);
      }
    }

    private void run(Api api) {
      String baseSourceDirectory = sourceDirectory + "/" + api.getPackageName().replace('.', '/');

      Collection<File> files = FileUtils.listFiles(
          new File(baseSourceDirectory),
          new SuffixFileFilter(api.getSuffixes().toArray(new String[]{})),
          TrueFileFilter.INSTANCE
      );

      TypeMap typeMap = api.getTypes() == null ? TypeMap.emptyTypeMap() : new TypeMap(api.getTypes());

        Converter converter = new JavaFlowConverter(typeMap);

        Execution execution = new Execution(
            new FileReader(),
            new JavaParser(),
            asList(
                new InheritanceTransformer(),
                new SortedTypeTransformer()
            ),
            singletonList(new MemberFieldsPresentVerifier(typeMap)),
            new FlowWriter(converter),
            asList(
                new CommentAppendTransformer("Generated by javaflow 1.1.0"),
                new EslintDisableTransformer(singletonList("no-use-before-define")),
                new CommentAppendTransformer("@flow")
            )
        );

        String flow = execution.run(
            files.stream().map(file -> {
              try {
                return file.getCanonicalPath();
              } catch (IOException e) {
                getLog().error(e);
                return "";
              }
            }).collect(Collectors.toList())
                .toArray(new String[]{})
        );

        try {
          Files.write(Paths.get(targetDirectory + "/" + api.getOutput()), asList(flow.split("\n")));
        } catch (IOException e) {
          getLog().error(e);
        }
    }
}