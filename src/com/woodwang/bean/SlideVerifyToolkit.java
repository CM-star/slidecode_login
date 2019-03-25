package com.woodwang.bean;

import org.apache.commons.codec.binary.Base64;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.text.NumberFormat;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

/**
*
* @Title:SlideVerifyToolkit
* @Description:滑动验证工具包
* @Comment :pictureTemplatesCut方法是提供给外界的方法。
*           1、newImage  滑块图片
*           2、oriCopyImage 验证图片，包含一块空白区域
*           3、X,Y坐标  用户滑动的坐标
* @author:woodwang
* @date :2019年3月12日
*/
public class SlideVerifyToolkit {
	
	private  int ORI_WIDTH = 300;//590;  //源文件宽度
    private  int ORI_HEIGHT = 150;//360;  //源文件高度
    private  int X;  //抠图坐标x
    private  int Y;  //抠图坐标y
    private  int WIDTH;  //模板图宽度
    private  int HEIGHT;  //模板图高度
    private  float xPercent;  //X位置移动百分比
    private  float yPercent;  //Y位置移动百分比
    
    public  int getX() {
        return X;
    }

    public  int getY() {
        return Y;
    }

    public  float getxPercent() {
        return xPercent;
    }

    public  float getyPercent() {
        return yPercent;
    }

    /**
     * 根据模板切图
     *
     * @param templateFile
     * @param targetFile
     * @param templateType
     * @param targetType
     * @return
     * @throws Exception
     */
    public  Map<String, String> pictureTemplatesCut(File templateFile, File targetFile, String templateType, String targetType) throws Exception {
        Map<String, String> pictureMap = new HashMap<>();
        // 文件类型
        String templateFiletype = templateType;
        String oriFiletype = targetType;
        if (isEmpty(templateFiletype) || isEmpty(oriFiletype)) {
            throw new RuntimeException("file type is empty");
        }
        // 源文件流
        File Orifile = targetFile;
        InputStream oriis = new FileInputStream(Orifile);

        // 模板图
        BufferedImage imageTemplate = ImageIO.read(templateFile);
        WIDTH = imageTemplate.getWidth();
        HEIGHT = imageTemplate.getHeight();
        generateCutoutCoordinates();
        // 最终图像
        BufferedImage newImage = new BufferedImage(WIDTH, HEIGHT, imageTemplate.getType());
        Graphics2D graphics = newImage.createGraphics();
        graphics.setBackground(Color.white);

        int bold = 5;
        
        // 获取感兴趣的目标区域
        BufferedImage targetImageNoDeal = getTargetArea(X, Y, WIDTH, HEIGHT, oriis, oriFiletype);

        // 根据模板图片抠图
        newImage = DealCutPictureByTemplate(targetImageNoDeal, imageTemplate, newImage);

        // 设置“抗锯齿”的属性
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setStroke(new BasicStroke(bold, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL));
        graphics.drawImage(newImage, 0, 0, null);
        graphics.dispose();

        ByteArrayOutputStream os = new ByteArrayOutputStream();//新建流。
        ImageIO.write(newImage, "png", os);//利用ImageIO类提供的write方法，将bi以png图片的数据模式写入流。
        byte[] newImages = os.toByteArray();

        // 源图生成遮罩
        BufferedImage oriImage = ImageIO.read(Orifile);
        byte[] oriCopyImages = DealOriPictureByTemplate(oriImage, imageTemplate, X, Y);

        Base64 base64Object = new Base64();
        pictureMap.put("newImage", base64Object.encodeToString(newImages));
        pictureMap.put("oriCopyImage", base64Object.encodeToString(oriCopyImages));
        pictureMap.put("pointX",String.valueOf(X));
        pictureMap.put("pointY",String.valueOf(Y));
        pictureMap.put("xPercent",String.valueOf(xPercent));
        pictureMap.put("yPercent",String.valueOf(yPercent));
        return pictureMap;
    }

    /**
     * 抠图后原图生成
     *
     * @param oriImage
     * @param templateImage
     * @param x
     * @param y
     * @return
     * @throws Exception
     */
    private  byte[] DealOriPictureByTemplate(BufferedImage oriImage, BufferedImage templateImage, int x,
                                                   int y) throws Exception {
        // 源文件备份图像矩阵 支持alpha通道的rgb图像
        BufferedImage ori_copy_image = new BufferedImage(oriImage.getWidth(), oriImage.getHeight(), BufferedImage.TYPE_4BYTE_ABGR);
        // 源文件图像矩阵
        int[][] oriImageData = getData(oriImage);
        // 模板图像矩阵
        int[][] templateImageData = getData(templateImage);

        //copy 源图做不透明处理
        int origWidth = oriImageData.length;
        int origHeight = oriImageData[0].length;
        for (int i = 0; i < origWidth; i++) {
            for (int j = 0; j < origHeight; j++) {
                int rgb = oriImage.getRGB(i, j);
                int r = (0xff & rgb);
                int g = (0xff & (rgb >> 8));
                int b = (0xff & (rgb >> 16));
                //无透明处理
                rgb = r + (g << 8) + (b << 16) + (255 << 24);
                ori_copy_image.setRGB(i, j, rgb);
            }
        }

        for (int i = 0; i < templateImageData.length; i++) {
            for (int j = 0; j < templateImageData[0].length - 5; j++) {
                int rgb = templateImage.getRGB(i, j);
                //对源文件备份图像(x+i,y+j)坐标点进行透明处理
                if (rgb != 16777215 && rgb <= 0 && i<origWidth && j<origHeight) {
                    int rgb_ori = ori_copy_image.getRGB(x + i, y + j);
                    int r = (0xff & rgb_ori);
                    int g = (0xff & (rgb_ori >> 8));
                    int b = (0xff & (rgb_ori >> 16));
                    rgb_ori = r + (g << 8) + (b << 16) + (150 << 24);
                    ori_copy_image.setRGB(x + i, y + j, rgb_ori);
                } else {
                    //do nothing
                }
            }
        }
        ByteArrayOutputStream os = new ByteArrayOutputStream();//新建流。
        ImageIO.write(ori_copy_image, "png", os);//利用ImageIO类提供的write方法，将bi以png图片的数据模式写入流。
        byte b[] = os.toByteArray();//从流中获取数据数组。
        return b;
    }

    /**
     * 根据模板图片抠图
     *
     * @param oriImage
     * @param templateImage
     * @return
     */
    private  BufferedImage DealCutPictureByTemplate(BufferedImage oriImage, BufferedImage templateImage,
                                                          BufferedImage targetImage) throws Exception {
        // 源文件图像矩阵
        int[][] oriImageData = getData(oriImage);
        int originalWidth = oriImage.getWidth();
        int originalHeight = oriImage.getHeight();
        // 模板图像矩阵
        int[][] templateImageData = getData(templateImage);
        // 模板图像宽度

        for (int i = 0; i < templateImageData.length; i++) {
            // 模板图片高度
            for (int j = 0; j < templateImageData[0].length; j++) {
                // 如果模板图像当前像素点不是白色 copy源文件信息到目标图片中
                int rgb = templateImageData[i][j];
                if (rgb != 16777215 && rgb <= 0 &&i<originalWidth&&j<originalHeight) {
                    targetImage.setRGB(i, j, oriImageData[i][j]);
                }
            }
        }
        return targetImage;
    }

    /**
     * 获取目标区域
     *
     * @param x            随机切图坐标x轴位置
     * @param y            随机切图坐标y轴位置
     * @param targetWidth  切图后目标宽度
     * @param targetHeight 切图后目标高度
     * @param ois          源文件输入流
     * @return
     * @throws Exception
     */
    private  BufferedImage getTargetArea(int x, int y, int targetWidth, int targetHeight, InputStream ois,
                                               String filetype) throws Exception {
        Iterator<ImageReader> imageReaderList = ImageIO.getImageReadersByFormatName(filetype);
        ImageReader imageReader = imageReaderList.next();
        // 获取图片流
        ImageInputStream iis = ImageIO.createImageInputStream(ois);
        // 输入源中的图像将只按顺序读取
        imageReader.setInput(iis, true);

        ImageReadParam param = imageReader.getDefaultReadParam();
        Rectangle rec = new Rectangle(x, y, targetWidth, targetHeight);
        param.setSourceRegion(rec);
        BufferedImage targetImage = imageReader.read(0, param);
        return targetImage;
    }

    /**
     * 生成图像矩阵
     *
     * @param
     * @return
     * @throws Exception
     */
    private  int[][] getData(BufferedImage bimg) throws Exception {
        int[][] data = new int[bimg.getWidth()][bimg.getHeight()];
        for (int i = 0; i < bimg.getWidth(); i++) {
            for (int j = 0; j < bimg.getHeight(); j++) {
                data[i][j] = bimg.getRGB(i, j);
            }
        }
        return data;
    }

    /**
     * 随机生成抠图坐标
     */
    private  void generateCutoutCoordinates() {
        Random random = new Random();
        int widthDifference = ORI_WIDTH - WIDTH;
        int heightDifference = ORI_HEIGHT - HEIGHT;

        if (widthDifference <= 0) {
            X = 5;
        } else {
            X = random.nextInt(ORI_WIDTH - WIDTH) + 5;
        }

        if (heightDifference <= 0) {
            Y = 5;
        } else {
            Y = random.nextInt(ORI_HEIGHT - HEIGHT) + 5;
        }

        NumberFormat numberFormat = NumberFormat.getInstance();
        numberFormat.setMaximumFractionDigits(2);

        xPercent = Float.parseFloat(numberFormat.format((float) X / (float) ORI_WIDTH));
        yPercent = Float.parseFloat(numberFormat.format((float) Y / (float) ORI_HEIGHT));
    }

    private  boolean isEmpty(String templateFiletype) {
        return templateFiletype == null || templateFiletype.equals("");
    }
}