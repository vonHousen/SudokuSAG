package sudoku;

import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.actor.testkit.typed.javadsl.TestProbe;
import akka.actor.typed.ActorRef;
import org.junit.ClassRule;
import org.junit.Test;

import java.util.Vector;

import static junit.framework.TestCase.assertEquals;

public class PlayerTest
{
	@ClassRule
	public static final TestKitJunitResource testKit = new TestKitJunitResource();

	@Test
	public void testRegisteringTables()
	{
		TestProbe<Player.RegisteredMsg> testProbe = testKit.createTestProbe();

		ActorRef<Player.Protocol> thePlayer = testKit.spawn(
				Player.create(
						new Player.CreateMsg(
								0,
								new Vector2d(0,0),
								Player.Type.ROW,
								new int[9],
								new boolean[9]
								)
				),"thePlayer");

		final int expectedCnt = 9;
		final int excesiveId = expectedCnt;
		Vector<ActorRef<Table.Protocol>> tables = new Vector<>();
		for(int id = 0; id < expectedCnt + 1; id++)
			tables.add(testKit.spawn(Table.create(
					new Table.CreateMsg(id, new Vector2d(id,0))
			),"table-" + id));

		for(int id = 0; id < expectedCnt; id++)
		{
			Vector2d position = new Vector2d(id, 0);
			thePlayer.tell(new Player.RegisterTableMsg(
					tables.get(id), position, testProbe.getRef()));
			Player.RegisteredMsg response = testProbe.receiveMessage();
			assertEquals(position, response._tablePos);
			assertEquals(true, response._isItDone);
		}

		Vector2d excessivePosition = new Vector2d(excesiveId, 0);
		thePlayer.tell(new Player.RegisterTableMsg(
				tables.get(excesiveId), excessivePosition, testProbe.getRef()));
		Player.RegisteredMsg response = testProbe.receiveMessage();
		assertEquals(excessivePosition, response._tablePos);
		assertEquals(false, response._isItDone);
	}
}
