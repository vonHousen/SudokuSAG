package sudoku;

import akka.actor.typed.javadsl.ActorContext;
import scala.Array;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class TableMemory
{
    /** Values of digits offered by Players. Indices represent Player. Zeros indicate currently unused slots. */
    private final int[] _offers;
    /** Structure of unique offers given by Players. */
    private final ArrayList<TableOffer> _uniqueOffers;
    /** Flags indicating that all _weightFlags for a specific Player are set to true for all the _uniqueOffers.
     * It means that the Table knows all the information it needs from that Player. */
    private final boolean[] _specifyFlags;
    /** Mask of forbidden digits. True means, the digit causes a conflict for some Player. */
    private final boolean[] _deniedMask;
    /** Number of offers currently proposed by Players (offers don't have to be unique). */
    private int _offerCount;
    /** Number of acceptance messages received from Players. Three messages guarantee insertion of _bestOffer digit. */
    private int _acceptanceCount;
    /** The offer (digit) chosen after negotiations with Players. Zero means, the offer was not chosen yet. */
    private int _bestOffer;

    public TableMemory(int sudokuSize)
    {
        this._offers = new int[3]; // By default initialized to 0
        this._uniqueOffers = new ArrayList<>();
        this._specifyFlags = new boolean[3]; // By default initialized to false
        this._deniedMask = new boolean[sudokuSize]; // By default initialized to false
        this._offerCount = 0;
        this._acceptanceCount = 0;
        this._bestOffer = 0;
    }

    /**
     * Get index of a specific offer in _uniqueOffers based on a given digit.
     * If no offer with the specified digit is present in _uniqueOffers, container size is returned.
     * @param digit value of digit of the searched offer
     * @return  index of _uniqueOffers, containing the searched offer
     */
    private int getUniqueOfferIndex(int digit)
    {
        int index = 0;
        for (TableOffer o : _uniqueOffers)
        {
            if (digit == o._digit)
            {
                break;
            }
            ++index;
        }
        return index;
    }

    /** Custom exception thrown when a Player is about to offer a digit the second time before rejection or withdrawal */
    public static class OverwriteOfferException extends RuntimeException
    {
        final int _playerInternalIndex;
        public OverwriteOfferException(String msg, int playerInternalIndex)
        {
            super(msg);
            this._playerInternalIndex = playerInternalIndex;
        }
    }

    public int getOfferCount() {return _offerCount;}

    public void addAcceptance() {++_acceptanceCount;}

    public int getAcceptanceCount() {return _acceptanceCount;}

    public int getBestOffer() {return  _bestOffer;}

    public void setBestOffer(int digit) {_bestOffer = digit;}

    public void setOffer(int n, int digit, int weight)
    {
        if (_offers[n] != 0)
        {
            throw new OverwriteOfferException("Cannot overwrite offer before rejecting or withdrawing it.", n);
        }
        ++_offerCount;
        _offers[n] = digit;
        final int digitIndex = getUniqueOfferIndex(digit);
        if (digitIndex == _uniqueOffers.size())
        {
            _uniqueOffers.add(new TableOffer(digit));
        }
        final TableOffer offerRef = _uniqueOffers.get(digitIndex);
        offerRef._weights[n] = weight;
        offerRef._weightFlags[n] = true;
    }

    public void setWeight(int n, int digit, float weight)
    {
        final int digitIndex = getUniqueOfferIndex(digit);
        final TableOffer offerRef = _uniqueOffers.get(digitIndex);
        offerRef._weights[n] = weight;
        offerRef._weightFlags[n] = true;
    }

    public int[] getUnknownDigits(int n)
    {
        Set<Integer> unknownDigitsSet = new HashSet<>();
        for (TableOffer o : _uniqueOffers)
        {
            if (!o._weightFlags[n])
            {
                unknownDigitsSet.add(o._digit);
            }
        }
        // Note: cannot use unknownDigitsSet.toArray(), because it returns Integer[] instead of int[]
        int[] unknownDigitsArray = new int[unknownDigitsSet.size()];
        int i = 0;
        for (Integer d : unknownDigitsSet)
        {
            unknownDigitsArray[i] = d;
            ++i;
        }
        return unknownDigitsArray;
    }

    /**
     * Withdraw all offers with a digit that causes conflict for some player.
     * @param digit value of offer causing conflict
     * @return  list of internal indices of Players that have their offers withdrawn
     */
    public ArrayList<Integer> withdrawDigit(int digit)
    {
        _deniedMask[digit-1] = true;
        ArrayList<Integer> playerIndices = new ArrayList<>();
        for (int i = 0; i < 3; ++i)
        {
            if (_offers[i] == digit)
            {
                playerIndices.add(i);
                --_offerCount;
            }
            _specifyFlags[i] = false;
        }
        final int digitIndex = getUniqueOfferIndex(digit);
        _uniqueOffers.remove(digitIndex);

        return playerIndices;
    }

    public void reset()
    {
        for (int i = 0; i < 3; ++i)
        {
            _offers[i] = 0;
            _specifyFlags[i] = false;
        }
        _uniqueOffers.clear();
        Arrays.fill(_deniedMask, false);
        _offerCount = 0;
        _acceptanceCount = 0;
        _bestOffer = 0;
    }
}
