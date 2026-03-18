package org.openremote.extension.entsoe.agent.protocol;

import org.openremote.model.AssetModelProvider;

public class EntsoeAgentModelProvider implements AssetModelProvider {

    @Override
    public boolean useAutoScan() {
        return true;
    }
}
