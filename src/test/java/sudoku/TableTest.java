package sudoku;

import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.actor.testkit.typed.javadsl.TestProbe;
import akka.actor.typed.*;
import org.junit.ClassRule;
import org.junit.Test;

import java.util.Arrays;
import java.util.Vector;

import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.assertTrue;

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
		// Prepare dummy Players and dummy Teacher
		TestProbe<Teacher.Protocol> teacherDummy = testKit.createTestProbe();
		TestProbe<Player.Protocol> playerDummy_1 = testKit.createTestProbe();
		TestProbe<Player.Protocol> playerDummy_2 = testKit.createTestProbe();
		TestProbe<Player.Protocol> playerDummy_3 = testKit.createTestProbe();

		// Create new Table to test
		ActorRef<Table.Protocol> theTable = testKit.spawn(
				Table.create(new Table.CreateMsg(0, new Position(0,0), 9)),"theTable1");

		// Register dummy Players
		theTable.tell(new Table.RegisterPlayerMsg(playerDummy_1.getRef(), 0, teacherDummy.getRef()));
		teacherDummy.receiveMessage();
		theTable.tell(new Table.RegisterPlayerMsg(playerDummy_2.getRef(), 9, teacherDummy.getRef()));
		teacherDummy.receiveMessage();
		theTable.tell(new Table.RegisterPlayerMsg(playerDummy_3.getRef(), 18, teacherDummy.getRef()));
		teacherDummy.receiveMessage();

		// Start "new iteration"
		theTable.tell(new Table.ResetMemoryMsg(teacherDummy.getRef()));
		Teacher.TablePerformedMemoryResetMsg response0 =
				(Teacher.TablePerformedMemoryResetMsg) teacherDummy.receiveMessage();
		assertEquals(0, response0._id);

		// Check Table's response for Player's offers
		theTable.tell(new Table.OfferMsg(1, 1L, playerDummy_1.getRef(), 0));
		playerDummy_1.expectNoMessage();
		playerDummy_2.expectNoMessage();
		playerDummy_3.expectNoMessage();
		theTable.tell(new Table.OfferMsg(2, 2L, playerDummy_2.getRef(), 9));
		playerDummy_1.expectNoMessage();
		playerDummy_2.expectNoMessage();
		playerDummy_3.expectNoMessage();
		theTable.tell(new Table.OfferMsg(3, 3L, playerDummy_3.getRef(), 18));
		Player.AdditionalInfoRequestMsg response1 = (Player.AdditionalInfoRequestMsg) playerDummy_1.receiveMessage();
		Player.AdditionalInfoRequestMsg response2 = (Player.AdditionalInfoRequestMsg) playerDummy_2.receiveMessage();
		Player.AdditionalInfoRequestMsg response3 = (Player.AdditionalInfoRequestMsg) playerDummy_3.receiveMessage();
		assertTrue(Arrays.equals(response1._otherDigits, new int[]{2, 3}));
		assertTrue(Arrays.equals(response2._otherDigits, new int[]{1, 3}));
		assertTrue(Arrays.equals(response3._otherDigits, new int[]{1, 2}));

		// Send to the Table more info about other offers
		theTable.tell(new Table.AdditionalInfoMsg(
				new int[]{2, 3}, new float[]{5L, 3L}, new boolean[]{false, false}, playerDummy_1.getRef(), 0));
		playerDummy_1.expectNoMessage();
		playerDummy_2.expectNoMessage();
		playerDummy_3.expectNoMessage();
		theTable.tell(new Table.AdditionalInfoMsg(
				new int[]{1, 3}, new float[]{100L, 2L}, new boolean[]{true, false}, playerDummy_2.getRef(), 9));
		Player.RejectOfferMsg responseReject = (Player.RejectOfferMsg) playerDummy_1.receiveMessage();
		playerDummy_2.expectNoMessage();
		playerDummy_3.expectNoMessage();
		theTable.tell(new Table.AdditionalInfoMsg(
				new int[]{1, 2}, new float[]{2L, 2L}, new boolean[]{false, false}, playerDummy_3.getRef(), 18));
		playerDummy_1.expectNoMessage();
		playerDummy_2.expectNoMessage();
		playerDummy_3.expectNoMessage();

		// Simulate replacing conflicting digit
		theTable.tell(new Table.OfferMsg(8, 3L, playerDummy_1.getRef(), 0));
		playerDummy_1.expectNoMessage();
		Player.AdditionalInfoRequestMsg response4 = (Player.AdditionalInfoRequestMsg) playerDummy_2.receiveMessage();
		Player.AdditionalInfoRequestMsg response5 = (Player.AdditionalInfoRequestMsg) playerDummy_3.receiveMessage();
		assertTrue(Arrays.equals(response4._otherDigits, new int[]{8}));
		assertTrue(Arrays.equals(response5._otherDigits, new int[]{8}));

		// Send to the Table more info about replaced offer
		theTable.tell(new Table.AdditionalInfoMsg(
				new int[]{8}, new float[]{1L}, new boolean[]{false, false}, playerDummy_2.getRef(), 9));
		playerDummy_1.expectNoMessage();
		playerDummy_2.expectNoMessage();
		playerDummy_3.expectNoMessage();
		theTable.tell(new Table.AdditionalInfoMsg(
				new int[]{8}, new float[]{2L}, new boolean[]{false, false}, playerDummy_3.getRef(), 18));
		Player.NegotiationsPositiveMsg responseOK1 = (Player.NegotiationsPositiveMsg) playerDummy_1.receiveMessage();
		Player.NegotiationsPositiveMsg responseOK2 = (Player.NegotiationsPositiveMsg) playerDummy_2.receiveMessage();
		Player.NegotiationsPositiveMsg responseOK3 = (Player.NegotiationsPositiveMsg) playerDummy_3.receiveMessage();
		assertEquals(2, responseOK1._approvedDigit);
		assertEquals(2, responseOK2._approvedDigit);
		assertEquals(2, responseOK3._approvedDigit);
	}
}
