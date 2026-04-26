package gd.script.gdcc.exception;

public class InvalidControlFlowGraphException extends GdccException {
    public InvalidControlFlowGraphException(String message) {
        super(message);
    }

    public InvalidControlFlowGraphException(String functionName, String reason) {
        super("Invalid control-flow graph in function '" + functionName + "': " + reason);
    }

    public InvalidControlFlowGraphException(String functionName, String blockId, String reason) {
        super("Invalid control-flow graph in function '" + functionName + "', block '" + blockId + "': " + reason);
    }

    public InvalidControlFlowGraphException(String functionName,
                                            String blockId,
                                            int index,
                                            String instruction,
                                            String reason) {
        super("Invalid control-flow graph in function '" + functionName + "', block '" + blockId +
                "', index " + index + " (" + instruction + "): " + reason);
    }
}
