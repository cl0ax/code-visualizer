package viz.model;

import java.util.List;

/** A public method found on the pasted class Solution. */
public record Sig(String name, String returnType, boolean isStatic, List<Param> params) {
    public record Param(String type, String name) {}
}
