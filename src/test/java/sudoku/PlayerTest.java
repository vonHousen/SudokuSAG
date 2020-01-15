package sudoku;

import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.actor.testkit.typed.javadsl.TestProbe;
import akka.actor.typed.ActorRef;
import org.junit.ClassRule;
import org.junit.Test;

import java.util.Vector;

import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.assertNull;

public class PlayerTest
{
	@ClassRule
	public static final TestKitJunitResource testKit = new TestKitJunitResource();

	@Test
	public void testRegisteringTables()
	{
		TestProbe<Teacher.Protocol> testTeacher = testKit.createTestProbe();

		final int thePlayerId = 9;
		final int expectedCnt = 9;

		ActorRef<Player.Protocol> thePlayer = testKit.spawn(
				Player.create(
						new Player.CreateMsg(thePlayerId, expectedCnt)
				),"thePlayer");

		final int excessiveId = expectedCnt;
		Vector<ActorRef<Table.Protocol>> tables = new Vector<>();
		for(int id = 0; id < expectedCnt + 1; id++)
			tables.add(testKit.spawn(Table.create(
					new Table.CreateMsg(id, new Position(id,0), expectedCnt)
			),"table-" + id));

		final int[] digits = {5,6,7,8,4,0,8,4,1};
		for(int id = 0; id < expectedCnt; id++)
		{
			thePlayer.tell(new Player.RegisterTableMsg(
					tables.get(id),
					id,
					digits[id],
					digits[id] == 0,
					testTeacher.getRef()
			));
			Teacher.RegisteredTableMsg response = (Teacher.RegisteredTableMsg) testTeacher.receiveMessage();
			assertEquals(id, response._tableId);
			assertEquals(true, response._isItDone);
		}

		final int[] tableIdsToTest = {0,1,2,3,4,5,6,7,8,9};
		thePlayer.tell(new Player.MemorisedDigitsRequestMsg(testTeacher.getRef(), tableIdsToTest));
		Teacher.MemorisedDigitsMsg reply = (Teacher.MemorisedDigitsMsg) testTeacher.receiveMessage();
		assertEquals(thePlayerId, reply._requestedPlayerId);
		for(int tableId = 0; tableId < 9; ++tableId)
		{
			assertEquals((int) digits[tableId], (int) reply._memorisedDigits.get(tableId).first);
			assertEquals(digits[tableId] == 0, reply._memorisedDigits.get(tableId).first == 0);
		}
		assertNull(reply._memorisedDigits.get(9));

		System.out.println("\n\t\t\t>>> IncorrectRegisterException expected <<< \n");
		thePlayer.tell(new Player.RegisterTableMsg(
				tables.get(excessiveId), excessiveId, 0, false, testTeacher.getRef()));
		Teacher.RegisteredTableMsg response = (Teacher.RegisteredTableMsg) testTeacher.receiveMessage();
		assertEquals(excessiveId, response._tableId);
		assertEquals(false, response._isItDone);
	}
}
