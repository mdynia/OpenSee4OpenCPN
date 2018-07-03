package org.dynia.seamap.slipp;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ImageCompress {

	private BufferedImage image;
	private List<Color> palette; 
	
	private FileOutputStream output;
	private Long outputOffset;
	private List<Long> lineOffsets;

	public ImageCompress(BufferedImage image, List<Color> palette, FileOutputStream fos) {
		this.image = image;
		this.palette = palette;	
		this.output = fos;		
	}

	/**
	 * Compress image and write to output stream.
	 * @param offset initial position in file
	 * @throws IOException
	 */
	public void comressImage(Long offset) throws IOException {
		this.outputOffset = offset;
		this.lineOffsets = new ArrayList<>();
				
		System.out.println("\tPosition ["+outputOffset+", 0x"+Long.toHexString(outputOffset)+"] (start)");
		System.out.println("\tNumber of lines to write ["+image.getHeight()+"]");
		
		//color depth
		write(7);		
		
		System.out.println("\tPosition ["+outputOffset+", 0x"+Long.toHexString(outputOffset)+"] (first line)");

		// required
		lineOffsets.add(0L);
		
		// compress and output all rows
		for (int y=0; y<image.getHeight(); y++) {
			lineOffsets.add(outputOffset);
			compressRow(y);
		}

		System.out.println("\tPosition ["+outputOffset+", 0x"+Long.toHexString(outputOffset)+"] (line idex)");
		// compress and output line offsets
		for (Long l: lineOffsets) {
			
			byte a = (byte)((l>>24) & 0xFF);
			byte b = (byte)((l>>16) & 0xFF);
			byte c = (byte)((l>>8) & 0xFF);
			byte d = (byte)((l) & 0xFF);

			write(a);
			write(b);
			write(c);
			write(d);
		}
		System.out.println("\tPosition ["+outputOffset+", 0x"+Long.toHexString(outputOffset)+"] (end)");
		
	}

	private void compressRow(int row) throws IOException {		

		//write row number
		int comp = 0;
		if (row>127) {
			comp = row/128 - 128;			
			write(comp);	
		} 
		comp = row % 128;
		write(comp); 


		int occurences = 0;
		int x=0;
		int currentColor = reduceToPalette(image.getRGB(x, row));
		if (currentColor == 0) {
			throw new RuntimeException("Color not found");
		}
		write(currentColor+128);

		while (x<image.getWidth()) {			

			int indexedColor = reduceToPalette(image.getRGB(x, row));
			
			if (indexedColor != currentColor) {

				//write how many additional occurences has previously selected color				
				writeColorOccurences(occurences - 1);
				occurences = 0;

				//write new color to be counted
				currentColor = indexedColor;
				int b = currentColor+128;
				write(b);	
			}
			x++;
			occurences++;			
		}
		writeColorOccurences(occurences-1);		

		//write end of "row"
		write(0);
	}



	/**
	 * Return index in the palette of the color that is "close" to the color given as input parameter
	 * @param rgb
	 * @return
	 */
	private int reduceToPalette(int rgb) {

		int blue = (rgb & 0xff);
		int green = ((rgb & 0xff00) >> 8);
		int red = ((rgb & 0xff0000) >> 16);

		int idx = 1;
		double minDist = 100;

		for (int i=0; i<palette.size();i++) {
			Color c= palette.get(i);
			double dist = Math.sqrt(Math.pow((c.getRed()-red),2) + Math.pow((c.getGreen()-green),2) + Math.pow((c.getBlue()-blue),2));					
			if (dist < minDist) {
				minDist = dist;
				idx = i+1;
			}
		}

		return idx;
	}


	private void write(int b) throws IOException {		
		output.write(b);
		outputOffset++;
	}
	
	private void writeColorOccurences(int occurences) throws IOException {
		int occComp = 0;
		if (occurences>127) {
			occComp = occurences/128 - 128;			
			write(occComp);
		} 
		occComp = occurences % 128;
		write(occComp);
	}
	

}
