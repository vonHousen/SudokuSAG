package sudoku;

import java.util.ArrayList;

public class TeacherMemory
{
    final private int _maxPlayerCount;

    final private int _maxTableCount;

    private int _maxTableFinishedCount;

    private int _playerResetCount;

    private int _tableResetCount;

    private int _tablesNotFinishedCount;

    private boolean[] _tablesNotFinished;

    public TeacherMemory(int playerCount, int tableCount, int finishedCount)
    {
        this._maxPlayerCount = playerCount;
        this._maxTableCount = tableCount;
        this._maxTableFinishedCount = finishedCount;
        this._playerResetCount = 0;
        this._tableResetCount = 0;
        this._tablesNotFinishedCount = tableCount;
        this._tablesNotFinished = new boolean[tableCount];
        for(int i = 0; i < _maxTableCount; ++i)
        {
            _tablesNotFinished[i] = true;
        }
    }

    public void setMaxTableFinishedCount(int count)
    {
        _maxTableFinishedCount = count;
    }

    private boolean allResetsCollected()
    {
        return _playerResetCount == _maxPlayerCount && _tableResetCount == _maxTableCount;
    }

    public boolean addPlayerReset()
    {
        ++_playerResetCount;
        return allResetsCollected();
    }

    public boolean addTableReset()
    {
        ++_tableResetCount;
        return allResetsCollected();
    }

    public int addTableFinished(int tableId)
    {
        _tablesNotFinished[tableId] = false;
        return --_tablesNotFinishedCount;
    }

    public int[] getTablesNotFinished()
    {
        final int[] notFinishedTableIds = new int[_tablesNotFinishedCount];
        for(int tableId = 0, j = 0; tableId < _maxTableCount; tableId++)
            if(_tablesNotFinished[tableId])
                notFinishedTableIds[j++] = tableId;

        return notFinishedTableIds;
    }

    void reset()
    {
        _playerResetCount = 0;
        _tableResetCount = 0;
        _tablesNotFinishedCount = _maxTableCount;
        for(int i = 0; i < _maxTableCount; ++i)
            _tablesNotFinished[i] = true;
    }
}
