package sudoku;

import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.actor.testkit.typed.javadsl.TestProbe;
import akka.actor.typed.*;
import org.junit.ClassRule;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertTrue;

public class TimerManagerTest
{
	@ClassRule
	public static final TestKitJunitResource testKit = new TestKitJunitResource();

	@Test
	public void simpleTest()
	{
		TestProbe<Teacher.Protocol> dummyTeacher = testKit.createTestProbe();

		ActorRef<TimerManager.Protocol> theTimer = testKit.spawn(
				TimerManager.create(new TimerManager.CreateMsg(dummyTeacher.getRef())), "timer-1");
		theTimer.tell(new TimerManager.RemindToCheckTablesMsg(100, new int[]{0,1}));
		dummyTeacher.expectMessageClass(Teacher.TablesAreNotRespondingMsg.class);
		theTimer.tell(new TimerManager.RemindToCheckTablesMsg(0, null));
		dummyTeacher.expectNoMessage();
	}

	@Test
	public void testParallelTimers()
	{
		TestProbe<Teacher.Protocol> dummyTeacher = testKit.createTestProbe();

		ActorRef<TimerManager.Protocol> theTimer = testKit.spawn(
				TimerManager.create(new TimerManager.CreateMsg(dummyTeacher.getRef())), "timer-2");
		theTimer.tell(new TimerManager.RemindToCheckTablesMsg(1000, new int[]{0,1,2}));
		dummyTeacher.expectNoMessage();
		theTimer.tell(new TimerManager.RemindToCheckTablesMsg(1000, new int[]{0,1}));
		dummyTeacher.expectNoMessage();
		theTimer.tell(new TimerManager.RemindToCheckTablesMsg(1000, new int[]{0}));
		Teacher.TablesAreNotRespondingMsg response = (Teacher.TablesAreNotRespondingMsg) dummyTeacher.receiveMessage();
		assertTrue(Arrays.equals(new int[]{0}, response._tableIds));
	}
}
