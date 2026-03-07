package com.Aplication.HARO.Config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "epayco")
public class EpaycoProperties {

    private String pCustIdCliente;
    private String pKey;
    private String validationUrl;

    public String getPCustIdCliente() { return pCustIdCliente; }
    public void setPCustIdCliente(String pCustIdCliente) { this.pCustIdCliente = pCustIdCliente; }

    public String getPKey() { return pKey; }
    public void setPKey(String pKey) { this.pKey = pKey; }

    public String getValidationUrl() { return validationUrl; }
    public void setValidationUrl(String validationUrl) { this.validationUrl = validationUrl; }
}
