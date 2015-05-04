package jenkins.plugins.logstash;

import static org.hamcrest.core.StringContains.containsString;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Arrays;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.runners.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

@SuppressWarnings("rawtypes")
@RunWith(MockitoJUnitRunner.class)
public class LogstashNotifierTest {
  // Extension of the unit under test that avoids making calls to Jenkins.getInstance() to get the DAO singleton
  static class MockLogstashNotifier extends LogstashNotifier {
    LogstashWriter writer;

    MockLogstashNotifier(int maxLines, boolean failBuild, LogstashWriter writer) {
      super(maxLines, failBuild);
      this.writer = writer;
    }

    @Override
    LogstashWriter getLogStashWriter(AbstractBuild<?, ?> build, OutputStream errorStream) {
      // Simulate bad Writer
      if(writer.isConnectionBroken()) {
        try {
          errorStream.write("Mocked Constructor failure".getBytes());
        } catch (IOException e) {
        }
      }
      return this.writer;
    }
  }

  @Mock AbstractBuild<?, ?> mockBuild;
  @Mock LogstashWriter mockWriter;
  @Mock Launcher mockLauncher;
  @Mock BuildListener mockListener;

  ByteArrayOutputStream errorBuffer;
  PrintStream errorStream;
  LogstashNotifier notifier;


  @Before
  public void before() throws Exception {
    errorBuffer = new ByteArrayOutputStream();
    errorStream = new PrintStream(errorBuffer, true);

    when(mockBuild.getLog(anyInt())).thenReturn(Arrays.asList("line 1", "line 2", "line 3"));

    when(mockListener.getLogger()).thenReturn(errorStream);

    // Initialize mocks
    when(mockWriter.isConnectionBroken()).thenReturn(false);
    Mockito.doNothing().when(mockWriter).writeBuildLog(anyInt());

    notifier = new MockLogstashNotifier(3, false, mockWriter);
  }

  @After
  public void after() throws Exception {
    verifyNoMoreInteractions(mockBuild);
    verifyNoMoreInteractions(mockLauncher);
    verifyNoMoreInteractions(mockListener);
    verifyNoMoreInteractions(mockWriter);
    errorStream.close();
  }

  @Test
  public void performSuccess() throws Exception {
    // Unit under test
    boolean result = notifier.perform(mockBuild, mockLauncher, mockListener);

    // Verify results
    assertTrue("Build should not be marked as failure", result);

    verify(mockListener).getLogger();
    verify(mockWriter).writeBuildLog(3);
    verify(mockWriter).isConnectionBroken();

    assertEquals("Errors were written", "", errorBuffer.toString());

  }

  @Test
  public void performBadWriterDoNotFailBuild() throws Exception {
    // Initialize mocks
    when(mockWriter.isConnectionBroken()).thenReturn(true);

    notifier = new MockLogstashNotifier(3, false, mockWriter);

    // Unit under test
    boolean result = notifier.perform(mockBuild, mockLauncher, mockListener);

    // Verify results
    assertTrue("Build should not be marked as failure", result);

    verify(mockListener).getLogger();
    verify(mockWriter).writeBuildLog(3);
    verify(mockWriter).isConnectionBroken();

    assertEquals("Error was not written", "Mocked Constructor failure", errorBuffer.toString());
  }

  @Test
  public void performBadWriterDoFailBuild() throws Exception {
    // Initialize mocks
    when(mockWriter.isConnectionBroken()).thenReturn(true);

    notifier = new MockLogstashNotifier(3, true, mockWriter);

    // Unit under test
    boolean result = notifier.perform(mockBuild, mockLauncher, mockListener);


    // Verify results
    assertFalse("Build should be marked as failure", result);

    verify(mockListener).getLogger();
    verify(mockWriter).writeBuildLog(3);
    verify(mockWriter, times(2)).isConnectionBroken();
    assertEquals("Error was not written", "Mocked Constructor failure", errorBuffer.toString());
  }

  @Test
  public void performWriteFailDoFailBuild() throws Exception {
    final String errorMsg = "[logstash-plugin]: Unable to serialize log data.\n" +
      "java.io.IOException: Unable to read log file\n";

    // Initialize mocks
    when(mockWriter.isConnectionBroken()).thenReturn(false).thenReturn(false).thenReturn(true);
    Mockito.doAnswer(new Answer() {
      @Override
      public Object answer(InvocationOnMock invocationOnMock) throws Throwable {
        if(!mockWriter.isConnectionBroken()) {
          errorBuffer.write(errorMsg.getBytes());
        }
        return null;
      }
    }).when(mockWriter).writeBuildLog(anyInt());

    notifier = new MockLogstashNotifier(3, true, mockWriter);
    assertEquals("Errors were written", "", errorBuffer.toString());

    // Unit under test
    boolean result = notifier.perform(mockBuild, mockLauncher, mockListener);

    // Verify results
    assertFalse("Build should be marked as failure", result);

    verify(mockListener).getLogger();
    verify(mockWriter).writeBuildLog(3);
    verify(mockWriter, times(3)).isConnectionBroken();

    assertThat("Wrong error message", errorBuffer.toString(), containsString(errorMsg));
  }

  @Test
  public void performAllLines() throws Exception {
    // Initialize mocks
    when(mockWriter.isConnectionBroken()).thenReturn(false);

    Mockito.doNothing().when(mockWriter).writeBuildLog(anyInt());

    notifier = new MockLogstashNotifier(-1, true, mockWriter);

    // Unit under test
    boolean result = notifier.perform(mockBuild, mockLauncher, mockListener);

    // Verify results
    assertTrue("Build should not be marked as failure", result);

    verify(mockListener).getLogger();
    verify(mockWriter).writeBuildLog(-1);
    verify(mockWriter, times(2)).isConnectionBroken();

    assertEquals("Errors were written", "", errorBuffer.toString());
  }

  @Test
  public void performZeroLines() throws Exception {
    // Initialize mocks
    when(mockWriter.isConnectionBroken()).thenReturn(false);

    Mockito.doNothing().when(mockWriter).writeBuildLog(anyInt());

    notifier = new MockLogstashNotifier(0, true, mockWriter);

    // Unit under test
    boolean result = notifier.perform(mockBuild, mockLauncher, mockListener);

    // Verify results
    assertTrue("Build should not be marked as failure", result);

    verify(mockListener).getLogger();
    verify(mockWriter).writeBuildLog(0);
    verify(mockWriter, times(2)).isConnectionBroken();

    assertEquals("Errors were written", "", errorBuffer.toString());
  }
}
