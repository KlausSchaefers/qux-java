package com.qux;

import java.awt.image.BufferedImage;
import java.io.InputStream;

import javax.imageio.ImageIO;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.Result;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;

public class QRCodeReader {

   
     public static String decode(InputStream whatFile) 
     {
       
         try 
         {
        	 BufferedImage tmpBfrImage = ImageIO.read(whatFile);
      
	         LuminanceSource tmpSource = new BufferedImageLuminanceSource(tmpBfrImage);
	         BinaryBitmap tmpBitmap = new BinaryBitmap(new HybridBinarizer(tmpSource));
	         MultiFormatReader tmpBarcodeReader = new MultiFormatReader();
	    
	        
	      
	         Result tmpResult = tmpBarcodeReader.decode(tmpBitmap);
	        
	         return String.valueOf(tmpResult.getText());
	       
	 
         } 
         catch (Exception e) 
         {
        	 e.printStackTrace();
         }
         
         return null;
    }
     
}