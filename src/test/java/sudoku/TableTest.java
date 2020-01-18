package sudoku;

import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.actor.testkit.typed.javadsl.TestProbe;
import akka.actor.typed.*;
import org.junit.ClassRule;
import org.junit.Test;

import java.util.Vector;

import static junit.framework.TestCase.assertEquals;

public class TableTest
{
	@ClassRule
	public static final TestKitJunitResource testKit = new TestKitJunitResource();

	@Test
	public void testRegisteringPlayers()
	{
		TestProbe<Teacher.Protocol> testProbe = testKit.createTestProbe();

		ActorRef<Table.Protocol> theTable = testKit.spawn(
				Table.create(new Table.CreateMsg(0, new Position(0,0), 9)),"theTable");

		Vector<ActorRef<Player.Protocol>> players = new Vector<>();
		for(int id = 0; id < 4; id++)
			players.add(testKit.spawn(Player.create(new Player.CreateMsg(id, 9)
			),"player-" + id));

		for(int id = 0; id < 3; id++)
		{
			theTable.tell(new Table.RegisterPlayerMsg(players.get(id), id, testProbe.getRef()));
			Teacher.RegisteredPlayerMsg response = (Teacher.RegisteredPlayerMsg) testProbe.receiveMessage();
			assertEquals(response._playerId, id);
			assertEquals(response._isItDone, true);
		}

		System.out.println("\n\t\t\t>>> IncorrectRegisterException expected <<< \n");
		theTable.tell(new Table.RegisterPlayerMsg(players.get(3), 3, testProbe.getRef()));
		Teacher.RegisteredPlayerMsg response = (Teacher.RegisteredPlayerMsg) testProbe.receiveMessage();
		assertEquals(response._playerId, 3);
		assertEquals(response._isItDone, false);
	}

	@Test
	public void testNegotiations()
	{
		TestProbe<Teacher.Protocol> teacher = testKit.createTestProbe();
		TestProbe<Player.Protocol> playerProbe1 = testKit.createTestProbe();
		TestProbe<Player.Protocol> playerProbe2 = testKit.createTestProbe();
		TestProbe<Player.Protocol> playerProbe3 = testKit.createTestProbe();

		ActorRef<Table.Protocol> theTable = testKit.spawn(
				Table.create(new Table.CreateMsg(0, new Position(0,0), 9)),"theTable");
		ActorRef<Player.Protocol> player1 = testKit.spawn(
				Player.create(new Player.CreateMsg(1, 9)));
		ActorRef<Player.Protocol> player2 = testKit.spawn(
				Player.create(new Player.CreateMsg(2, 9)));
		ActorRef<Player.Protocol> player3 = testKit.spawn(
				Player.create(new Player.CreateMsg(3, 9)));

		theTable.tell(new Table.RegisterPlayerMsg(player1, 1, teacher.getRef()));
		theTable.tell(new Table.RegisterPlayerMsg(player2, 2, teacher.getRef()));
		theTable.tell(new Table.RegisterPlayerMsg(player3, 3, teacher.getRef()));


		theTable.tell(new Table.OfferMsg(1, 1, playerProbe1.getRef(), 1));
		playerProbe1.expectNoMessage();
		theTable.tell(new Table.OfferMsg(2, 2, playerProbe2.getRef(), 2));
		playerProbe2.expectNoMessage();
		theTable.tell(new Table.OfferMsg(3, 3, playerProbe3.getRef(), 3));
		Player.AdditionalInfoRequestMsg response1 = (Player.AdditionalInfoRequestMsg) playerProbe1.receiveMessage();
		Player.AdditionalInfoRequestMsg response2 = (Player.AdditionalInfoRequestMsg) playerProbe2.receiveMessage();
		Player.AdditionalInfoRequestMsg response3 = (Player.AdditionalInfoRequestMsg) playerProbe3.receiveMessage();
		assertEquals(new int[] {2,3},  response1._otherDigits);
		assertEquals(new int[] {1,3},  response2._otherDigits);
		assertEquals(new int[] {1,2},  response3._otherDigits);
	}
}
