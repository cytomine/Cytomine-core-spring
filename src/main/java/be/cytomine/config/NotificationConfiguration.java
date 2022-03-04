package be.cytomine.config;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class NotificationConfiguration {

    private String email;

    private String password;

    private String smtpHost;

    private String smtpPort;

    private String smtpProtocol;

    private Boolean smtpStarttlsRequired;

    private Boolean smtpStarttlsEnable;

    private Boolean smtpDebug;
}
