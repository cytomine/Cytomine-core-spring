package be.cytomine.service.search;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class UserSearchExtension {
    private boolean withRoles;

    private boolean withLastImage;

    private boolean withLastConnection;

    private boolean withNumberConnections;

    private boolean withUserJob;

    public boolean noExtension() {
        return !withRoles && !withLastImage && !withLastConnection && !withNumberConnections && !withUserJob;
    }
}