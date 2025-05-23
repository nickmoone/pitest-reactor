package org.pitest.maven;

import org.apache.maven.model.Build;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.mockito.Mock;
import org.pitest.coverage.CoverageSummary;
import org.pitest.mutationtest.config.PluginServices;
import org.pitest.mutationtest.config.ReportOptions;
import org.pitest.mutationtest.statistics.MutationStatistics;
import org.pitest.mutationtest.statistics.Score;
import org.pitest.mutationtest.tooling.CombinedStatistics;

import java.io.File;
import java.util.Collections;

import static java.util.Arrays.asList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class PitMojoTest extends BasePitMojoTest {

  @Mock
  private MavenProject executionProject;

  private PitMojo testee;

  @Override
  public void setUp() throws Exception {
    super.setUp();

    when(this.project.getExecutionProject()).thenReturn(executionProject);
    when(this.project.getBasedir()).thenReturn(new File("BASEDIR"));
    when(this.executionProject.getBasedir()).thenReturn(new File("BASEDIR"));
  }

  public void testRunsAMutationReportWhenMutationCoverageGoalTriggered()
      throws Exception {
    this.testee = createPITMojo(createPomWithConfiguration(""));
    final Build build = new Build();
    build.setOutputDirectory("foo");
    this.testee.getProject().setBuild(build);
    this.testee.execute();
    verify(this.executionStrategy).execute(any(File.class),
        any(ReportOptions.class), any(PluginServices.class), anyMap());
  }

  public void testDoesNotAnalysePomProjects() throws Exception {
    when(this.project.getPackaging()).thenReturn("pom");
    this.testee = createPITMojo(createPomWithConfiguration(""));
    this.testee.execute();
    verify(this.executionStrategy, never()).execute(any(File.class),
        any(ReportOptions.class), any(PluginServices.class), anyMap());
  }

  public void testDoesNotAnalyseProjectsWithSkipFlagSet() throws Exception {
    this.testee = createPITMojo(createPomWithConfiguration("<skip>true</skip>"));
    this.testee.execute();
    verify(this.executionStrategy, never()).execute(any(File.class),
        any(ReportOptions.class), any(PluginServices.class), anyMap());
  }

  public void testThrowsMojoFailureExceptionWhenMutationScoreBelowThreshold()
      throws Exception {
    this.testee = createPITMojo(createPomWithConfiguration("<mutationThreshold>21</mutationThreshold>"));
    setupCoverage(20, 1, 1);
    try {
      this.testee.execute();
      fail();
    } catch (final MojoFailureException ex) {
      // pass
    }
  }

  public void testThrowsMojoFailureExceptionWhenTestStrengthScoreBelowThreshold()
          throws Exception {
    this.testee = createPITMojo(createPomWithConfiguration("<testStrengthThreshold>21</testStrengthThreshold>"));
    setupTestStrength(120, 20, 100);
    try {
      this.testee.execute();
      fail();
    } catch (final MojoFailureException ex) {
      // pass
    }
  }

  public void testDoesNotThrowsMojoFailureExceptionWhenMutationScoreOnThreshold()
      throws Exception {
    this.testee = createPITMojo(createPomWithConfiguration("<mutationThreshold>21</mutationThreshold>"));
    setupCoverage(21, 1, 1);
    try {
      this.testee.execute();
      // pass
    } catch (final MojoFailureException ex) {
      fail();
    }
  }

  public void testThrowsMojoFailureExceptionWhenSurvivingMutantsAboveThreshold()
      throws Exception {
    this.testee = createPITMojo(createPomWithConfiguration("<maxSurviving>19</maxSurviving>"));
    setupSuvivingMutants(20);
    try {
      this.testee.execute();
      fail();
    } catch (final MojoFailureException ex) {
      // pass
    }
  }
  
  public void testDoesNotThrowsMojoFailureExceptionWhenSurvivingMutantsOnThreshold()
      throws Exception {
    this.testee = createPITMojo(createPomWithConfiguration("<maxSurviving>19</maxSurviving>"));
    setupSuvivingMutants(19);
    try {
      this.testee.execute();
    } catch (final MojoFailureException ex) {
      fail();
    }
  }
  
  public void testAllowsSurvivingMutantsThresholdToBeZero()
      throws Exception {
    this.testee = createPITMojo(createPomWithConfiguration("<maxSurviving>0</maxSurviving>"));
    setupSuvivingMutants(1);
    try {
      this.testee.execute();
      fail();
    } catch (final MojoFailureException ex) {
      // pass
    }
  }
  
  public void testThrowsMojoFailureExceptionWhenCoverageBelowThreshold()
      throws Exception {
    this.testee = createPITMojo(createPomWithConfiguration("<coverageThreshold>50</coverageThreshold>"));
    setupCoverage(100L, 100, 40);
    try {
      this.testee.execute();
      fail();
    } catch (final MojoFailureException ex) {
      // pass
    }
  }

  public void testDoesNotThrowMojoFailureExceptionWhenCoverageOnThreshold()
      throws Exception {
    this.testee = createPITMojo(createPomWithConfiguration("<coverageThreshold>50</coverageThreshold>"));
    setupCoverage(100L, 100, 50);
    try {
      this.testee.execute();
      // pass
    } catch (final MojoFailureException ex) {
      fail();
    }
  }

  public void testConfigureEnvironmentVariable() throws Exception {

    PitMojo mojo = createPITMojo(createPomWithConfiguration("\n"
        + "                    <environmentVariables>\n"
        + "                        <DISPLAY>:20</DISPLAY>\n"
        + "                    </environmentVariables>"));

    assertEquals(mojo.getEnvironmentVariables().get("DISPLAY"), ":20");
  }

  public void testEmptyTargetClassIsIgnored() throws Exception{

    PitMojo mojo = createPITMojo(createPomWithConfiguration("\n"
        + "  <targetClasses>\n"
        + "    <targetClass>net.example.ClassName</targetClass>\n"
        + "    <targetClass>net.example.Other</targetClass>\n"
        + "    <targetClass></targetClass>\n"
        + "  </targetClasses>"));

    assertEquals(
        asList("net.example.ClassName", "net.example.Other"),
        mojo.getTargetClasses());
  }

  public void testEmptyTargetTestIsIgnored() throws Exception{

    PitMojo mojo = createPITMojo(createPomWithConfiguration("\n"
        + "  <targetTests>\n"
        + "    <targetTest>net.example.ClassNameTest</targetTest>\n"
        + "    <targetTest>net.example.OtherTest</targetTest>\n"
        + "    <targetTest></targetTest>\n"
        + "  </targetTests>"));

    assertEquals(
        asList("net.example.ClassNameTest", "net.example.OtherTest"),
        mojo.getTargetTests());
  }


  public void testEmptyExcludedMethodIsIgnored() throws Exception{

    PitMojo mojo = createPITMojo(createPomWithConfiguration("\n"
        + "  <excludedMethods>\n"
        + "    <excludedMethod>*method1</excludedMethod>\n"
        + "    <excludedMethod>*method2</excludedMethod>\n"
        + "    <excludedMethod></excludedMethod>\n"
        + "  </excludedMethods>"));

    assertEquals(
        asList("*method1", "*method2"),
        mojo.getExcludedMethods());
  }

  public void testEmptyExcludedClassIsIgnored() throws Exception{

    PitMojo mojo = createPITMojo(createPomWithConfiguration("\n"
        + "  <excludedClasses>\n"
        + "    <excludedClass>net.example.BadClass</excludedClass>\n"
        + "    <excludedClass>net.example.WorstClass</excludedClass>\n"
        + "    <excludedClass></excludedClass>\n"
        + "  </excludedClasses>"));

    assertEquals(
        asList("net.example.BadClass", "net.example.WorstClass"),
        mojo.getExcludedClasses());
  }

  public void testEmptyAvoidCallsToValueIsIgnored() throws Exception{

    PitMojo mojo = createPITMojo(createPomWithConfiguration("\n"
        + "  <avoidCallsTo>\n"
        + "    <avoidCallsTo>net.example.methodA</avoidCallsTo>\n"
        + "    <avoidCallsTo>net.example.methodB</avoidCallsTo>\n"
        + "    <avoidCallsTo></avoidCallsTo>\n"
        + "  </avoidCallsTo>"));

    assertEquals(
        asList("net.example.methodA", "net.example.methodB"),
        mojo.getAvoidCallsTo());
  }

  public void testEmptyMutatorIsIgnored() throws Exception{

    PitMojo mojo = createPITMojo(createPomWithConfiguration("\n"
        + "  <mutators>\n"
        + "    <mutator>MUTATOR_1</mutator>\n"
        + "    <mutator>MUTATOR_2</mutator>\n"
        + "    <mutator></mutator>\n"
        + "  </mutators>"));

    assertEquals(
        asList("MUTATOR_1", "MUTATOR_2"),
        mojo.getMutators());
  }

  public void testEmptyExcludedTestClassIsIgnored() throws Exception {

    PitMojo mojo = createPITMojo(createPomWithConfiguration("\n"
        + "  <excludedTestClasses>\n"
        + "    <excludedTestClass>TestClass1</excludedTestClass>\n"
        + "    <excludedTestClass>TestClass2</excludedTestClass>\n"
        + "    <excludedTestClass></excludedTestClass>\n"
        + "  </excludedTestClasses>"));

    assertEquals(
        asList("TestClass1", "TestClass2"),
        mojo.getExcludedTestClasses());
  }

  public void testEmptyJvmArgIsIgnored() throws Exception {

    PitMojo mojo = createPITMojo(createPomWithConfiguration("\n"
        + "  <jvmArgs>\n"
        + "    <jvmArg>-Dnet.sample.param=42</jvmArg>\n"
        + "    <jvmArg>-Dnet.sample.fun=true</jvmArg>\n"
        + "    <jvmArg></jvmArg>\n"
        + "  </jvmArgs>"));

    assertEquals(
        asList("-Dnet.sample.param=42", "-Dnet.sample.fun=true"),
        mojo.getJvmArgs());
  }

  public void testEmptyOutputFormatIsIgnored() throws Exception {

    PitMojo mojo = createPITMojo(createPomWithConfiguration("\n"
        + "  <outputFormats>\n"
        + "    <outputFormat>XML</outputFormat>\n"
        + "    <outputFormat>HTML</outputFormat>\n"
        + "    <outputFormat></outputFormat>\n"
        + "  </outputFormats>"));

    assertEquals(
        asList("XML", "HTML"),
        mojo.getOutputFormats());
  }

  public void testEmptyExcludedGroupIsIgnored() throws Exception {

    PitMojo mojo = createPITMojo(createPomWithConfiguration("\n"
        + "  <excludedGroups>\n"
        + "    <excludedGroup>REDS</excludedGroup>\n"
        + "    <excludedGroup>GREENS</excludedGroup>\n"
        + "    <excludedGroup></excludedGroup>\n"
        + "  </excludedGroups>"));

    assertEquals(
        asList("REDS", "GREENS"),
        mojo.getExcludedGroups());
  }

  public void testEmptyIncludedGroupIsIgnored() throws Exception {

    PitMojo mojo = createPITMojo(createPomWithConfiguration("\n"
        + "  <includedGroups>\n"
        + "    <includedGroup>YELLOWS</includedGroup>\n"
        + "    <includedGroup>PURPLES</includedGroup>\n"
        + "    <includedGroup></includedGroup>\n"
        + "  </includedGroups>"));

    assertEquals(
        asList("YELLOWS", "PURPLES"),
        mojo.getIncludedGroups());
  }

  public void testEmptyIncludedTestMethodIsIgnored() throws Exception {

    PitMojo mojo = createPITMojo(createPomWithConfiguration("\n"
        + "  <includedTestMethods>\n"
        + "    <includedTestMethod>testA</includedTestMethod>\n"
        + "    <includedTestMethod>testB</includedTestMethod>\n"
        + "    <includedTestMethod></includedTestMethod>\n"
        + "  </includedTestMethods>"));

    assertEquals(
        asList("testA", "testB"),
        mojo.getIncludedTestMethods());
  }

  public void testEmptyAdditionalClasspathElementIsIgnored() throws Exception {

    PitMojo mojo = createPITMojo(createPomWithConfiguration("\n"
        + "  <additionalClasspathElements>\n"
        + "    <additionalClasspathElement>stuff.jar</additionalClasspathElement>\n"
        + "    <additionalClasspathElement>thing.jar</additionalClasspathElement>\n"
        + "    <additionalClasspathElement></additionalClasspathElement>\n"
        + "  </additionalClasspathElements>"));

    assertEquals(
        asList("stuff.jar", "thing.jar"),
        mojo.getAdditionalClasspathElements());
  }

  public void testEmptyClasspathDependencyExcludeIsIgnored() throws Exception {

    PitMojo mojo = createPITMojo(createPomWithConfiguration("\n"
        + "  <classpathDependencyExcludes>\n"
        + "    <classpathDependencyExclude>bad.jar</classpathDependencyExclude>\n"
        + "    <classpathDependencyExclude>unwanted.jar</classpathDependencyExclude>\n"
        + "    <classpathDependencyExclude></classpathDependencyExclude>\n"
        + "  </classpathDependencyExcludes>"));

    assertEquals(
        asList("bad.jar", "unwanted.jar"),
        mojo.getClasspathDependencyExcludes());
  }

  public void testEmptyExcludedRunnerIsIgnored() throws Exception {

    PitMojo mojo = createPITMojo(createPomWithConfiguration("\n"
        + "  <excludedRunners>\n"
        + "    <excludedRunner>SimpleRunner</excludedRunner>\n"
        + "    <excludedRunner>FastRunner</excludedRunner>\n"
        + "    <excludedRunner></excludedRunner>\n"
        + "  </excludedRunners>"));

    assertEquals(
        asList("SimpleRunner", "FastRunner"),
        mojo.getExcludedRunners());
  }

  public void testEmptyFeatureIsIgnored() throws Exception {

    PitMojo mojo = createPITMojo(createPomWithConfiguration("\n"
        + "  <features>\n"
        + "    <feature>DO_THAT_THING</feature>\n"
        + "    <feature>BE_AWESOME</feature>\n"
        + "    <feature></feature>\n"
        + "  </features>"));

    assertEquals(
        asList("DO_THAT_THING", "BE_AWESOME"),
        mojo.getFeatures());
  }

  public void testCombinesFeaturesAndExtraFeatures() throws Exception {

    PitMojo mojo = createPITMojo(createPomWithConfiguration("\n"
            + "  <features>\n"
            + "    <feature>FEATURE</feature>\n"
            + "  </features>\n"
            + "  <extraFeatures>\n"
            + "    <feature>ALSO_A_FEATURE</feature>\n"
            + "    <feature>MORE</feature>\n"
            + "  </extraFeatures>\n"
    ));

    assertEquals(
            asList("FEATURE", "ALSO_A_FEATURE", "MORE"),
            mojo.getFeatures());
  }

  private void setupCoverage(long mutationScore, int lines, int linesCovered)
      throws MojoExecutionException {
    Iterable<Score> scores = Collections.<Score>emptyList();
    final MutationStatistics stats = new MutationStatistics(scores, 100, mutationScore, 100, 0, Collections.emptySet());
    CoverageSummary sum = new CoverageSummary(lines, linesCovered, 0);
    final CombinedStatistics cs = new CombinedStatistics(stats, sum, Collections.emptyList());
    when(
        this.executionStrategy.execute(any(File.class),
            any(ReportOptions.class), any(PluginServices.class), anyMap()))
            .thenReturn(cs);
  }

  private void setupTestStrength(long totalMutations, long mutationDetected, long mutationsWithCoverage)
          throws MojoExecutionException {
    Iterable<Score> scores = Collections.<Score>emptyList();
    final MutationStatistics stats = new MutationStatistics(scores, totalMutations, mutationDetected, mutationsWithCoverage, 0, Collections.emptySet());
    CoverageSummary sum = new CoverageSummary(0, 0, 0);
    final CombinedStatistics cs = new CombinedStatistics(stats, sum, Collections.emptyList());
    when(
            this.executionStrategy.execute(any(File.class),
                    any(ReportOptions.class), any(PluginServices.class), anyMap()))
            .thenReturn(cs);
  }
  
  private void setupSuvivingMutants(long survivors)
      throws MojoExecutionException {
    Iterable<Score> scores = Collections.<Score>emptyList();
    int detected = 100;
    final MutationStatistics stats = new MutationStatistics(scores, detected + survivors, detected, detected + survivors, 0, Collections.emptySet());
    CoverageSummary sum = new CoverageSummary(0, 0, 0);
    final CombinedStatistics cs = new CombinedStatistics(stats, sum, Collections.emptyList());
    when(
        this.executionStrategy.execute(any(File.class),
            any(ReportOptions.class), any(PluginServices.class), anyMap()))
            .thenReturn(cs);
  }

}
