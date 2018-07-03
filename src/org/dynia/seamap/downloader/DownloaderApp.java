package org.dynia.seamap.downloader;

import java.io.IOException;

public class DownloaderApp {

	public static void main(String[] args) throws IOException {

		if (args.length == 0 || args[0] == "") {
			System.err.println("Please specify config file!");
			return;
		}
		
		System.out.println("-- DOWNLOADING --");
		Downloader d = new Downloader();

		// initialize parameters from config file
		d.init(args[0]);
		
		// connect to the Internet and download all map fragments
		d.downloadLayers();
		
		d.flattenLayers();
		d.saveKapFile();
		System.out.println("SUCCESSFUL CREATION OF KAP FILE:\n- See downloaded sources in 'src' folder.\n- See resulting .kap file.\nDONE.");

	}
	
	
}
