package org.witness.informacam.models.transport;

import java.io.Serializable;

import org.witness.informacam.models.Model;

public class ITransportData extends Model implements Serializable {
	private static final long serialVersionUID = 875084964718311617L;
	
	public String assetPath = null;
	public String assetName = null;
	public String mimeType = null;
	public String key = null;
	
	public ITransportData() {
		super();
	}
	
	public ITransportData(ITransportData transportData) {
		super();
		inflate(transportData);
	}
}