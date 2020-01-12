package sudoku;

import java.util.HashMap;
import java.util.Map;

/**
 * Map from global Table id to internal index and Table reference.
 * Data structure for storing Tables - agents registered to this Player.
 */
public class AgentMap<T>
{
    /** Map from global agent id to internal index. */
    private final Map<Integer, Integer> _indices;
    /** Array of agent references indexed internally. */
    private Object[] _agents;
    /** Number of agents currently registered. */
    private int _agentCount;

    public AgentMap(int maxAgentCount)
    {
        _indices = new HashMap<>();
        _agents = new Object[maxAgentCount];
        _agentCount = 0;
    }

    /**
     * Get agent reference by internal index.
     * @param i internal index
     * @return  agent reference
     */
    public T getAgent(int i)
    {
        @SuppressWarnings("unchecked")
        final T a = (T) _agents[i];
        return a;
    }

    /**
     * Get agent reference by global id.
     * @param id    agent global id
     * @return  	agent reference
     */
    public T getAgentById(int id)
    {
        return getAgent(getIndex(id));
    }

    /**
     * Get internal index by global id.
     * @param id    agent global id
     * @return  	internal index
     */
    public int getIndex(int id)
    {
        return _indices.getOrDefault(id,-1);
    }

    /**
     * Register new agent in the map.
     * Does not perform a check for registering more agents than it should.
     * @param globalId  registered agent's global id
     * @param agentRef  reference to the registered agent
     */
    public void register(int globalId, T agentRef)
    {
        _indices.put(globalId, _agentCount);
        _agents[_agentCount] = agentRef;
        ++_agentCount;
    }

    /**
     * Check whether more agents can be registered.
     * @return  true, if cannot register more agents
     */
    public boolean isFull()
    {
        return _agentCount == _agents.length;
    }
}
