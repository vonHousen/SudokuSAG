package sudoku;

import java.util.HashSet;
import java.util.Set;

public class TeacherMemory
{
    final private int _maxPlayerCount;

    final private int _maxTableCount;

    private int _playerRewardedCount;

    private int _playerResetCount;

    private int _tableResetCount;

    final private Set<Integer> _tablesNotFinished;

    final private Set<Integer> _normalTables;

    public TeacherMemory(int playerCount, int tableCount, HashSet<Integer> normalTables)
    {
        this._maxPlayerCount = playerCount;
        this._maxTableCount = tableCount;
        this._playerRewardedCount = 0;
        this._playerResetCount = 0;
        this._tableResetCount = 0;
        this._tablesNotFinished = new HashSet<>();
        _tablesNotFinished.addAll(normalTables);
        this._normalTables = new HashSet<>();
        _normalTables.addAll(normalTables);
    }

    public void setNormalTables(HashSet<Integer> normalTables)
    {
        _normalTables.clear();
        _normalTables.addAll(normalTables);
    }

    private boolean allResetsCollected()
    {
        if(_playerResetCount > _maxPlayerCount || _tableResetCount > _maxTableCount)
            throw new RuntimeException("Too many resets!");
        return _playerResetCount == _maxPlayerCount && _tableResetCount == _maxTableCount;
    }

    public boolean addPlayerRewarded()
    {
        ++_playerRewardedCount;
        return _playerRewardedCount == _maxPlayerCount;
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
        if(!_tablesNotFinished.remove(tableId)) // if table is not present in the NotFinished Set...
            throw new RuntimeException("Table finished second time!");

        return _tablesNotFinished.size();
    }

    public int[] getTablesNotFinished()
    {
        final int[] notFinishedTableIds = new int[_tablesNotFinished.size()];
        int i = 0;
        for(int notFinishedTableId : _tablesNotFinished)
            notFinishedTableIds[i++] = notFinishedTableId;

        return notFinishedTableIds;
    }

    void reset()
    {
        _playerRewardedCount = 0;
        _playerResetCount = 0;
        _tableResetCount = 0;
        _tablesNotFinished.clear();
        _tablesNotFinished.addAll(_normalTables);
    }
}
