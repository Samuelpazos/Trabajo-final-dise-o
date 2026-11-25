package co.nexoeventos.servicio;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import com.google.zxing.*;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.qrcode.QRCodeWriter;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xddf.usermodel.chart.*;
import org.apache.poi.xssf.usermodel.*;
import org.springframework.stereotype.Service;

@Service
public class ArchivoServicio {
    public record Tabla(String titulo, List<String> resumen, List<String> columnas, List<List<?>> filas,
                        Map<String,? extends Number> serie) {}

    public byte[] qr(String codigo) {
        try (ByteArrayOutputStream salida=new ByteArrayOutputStream()) {
            MatrixToImageWriter.writeToStream(new QRCodeWriter().encode(codigo,BarcodeFormat.QR_CODE,300,300),"PNG",salida);
            return salida.toByteArray();
        } catch (IOException | WriterException error) {
            throw new UncheckedIOException(new IOException("No se pudo crear el código QR",error));
        }
    }

    public byte[] pdf(Tabla tabla) {
        List<String> lineas=new ArrayList<>();
        for (String resumen : tabla.resumen()) lineas.addAll(partir(resumen));
        if (!tabla.columnas().isEmpty()) {
            lineas.add("");
            lineas.addAll(partir(String.join("  |  ",tabla.columnas())));
            for (List<?> fila : tabla.filas()) lineas.addAll(partir(unir(fila)));
        }
        try (PDDocument pdf=new PDDocument(); ByteArrayOutputStream salida=new ByteArrayOutputStream()) {
            pdf.getDocumentInformation().setTitle(tabla.titulo());
            PDFont fuente=new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDFont titulo=new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            int porPagina=46;
            for (int inicio=0;inicio<Math.max(1,lineas.size());inicio+=porPagina) {
                PDPage pagina=new PDPage(PDRectangle.A4);
                pdf.addPage(pagina);
                try (PDPageContentStream pluma=new PDPageContentStream(pdf,pagina)) {
                    escribir(pluma,titulo,19,52,789,tabla.titulo());
                    escribir(pluma,fuente,9,52,768,"Nexo Eventos  ·  Página "+pdf.getNumberOfPages());
                    for (int i=inicio;i<Math.min(inicio+porPagina,lineas.size());i++)
                        escribir(pluma,fuente,9,52,739-15*(i-inicio),lineas.get(i));
                }
            }
            if (!tabla.serie().isEmpty()) graficoPdf(pdf,fuente,titulo,tabla.serie());
            pdf.save(salida);
            return salida.toByteArray();
        } catch (IOException error) { throw new UncheckedIOException(error); }
    }

    private void graficoPdf(PDDocument pdf,PDFont fuente,PDFont titulo,Map<String,? extends Number> serie) throws IOException {
        Iterator<? extends Map.Entry<String,? extends Number>> valores=serie.entrySet().iterator();
        double max=Math.max(1,serie.values().stream().mapToDouble(Number::doubleValue).max().orElse(1));
        while (valores.hasNext()) {
            PDPage pagina=new PDPage(PDRectangle.A4);
            pdf.addPage(pagina);
            try (PDPageContentStream pluma=new PDPageContentStream(pdf,pagina)) {
                escribir(pluma,titulo,19,52,789,"Evolución diaria");
                for (int fila=0;fila<32 && valores.hasNext();fila++) {
                    var valor=valores.next();
                    float y=752-fila*22;
                    escribir(pluma,fuente,9,52,y,valor.getKey());
                    pluma.setNonStrokingColor(0.05f,0.45f,0.39f);
                    pluma.addRect(129,y-2,(float)(Math.max(0,valor.getValue().doubleValue())*330/max),11);
                    pluma.fill();
                    escribir(pluma,fuente,9,468,y,valor.getValue().toString());
                }
            }
        }
    }

    private void escribir(PDPageContentStream p,PDFont f,int tam,float x,float y,String valor) throws IOException {
        p.setNonStrokingColor(0.12f,0.16f,0.23f);
        p.beginText();
        p.setFont(f,tam);
        p.newLineAtOffset(x,y);
        p.showText(valor.replaceAll("[^\\x20-\\x7e\\xA0-\\xFF]","?"));
        p.endText();
    }

    private List<String> partir(String valor) {
        List<String> lineas=new ArrayList<>();
        for (String linea : valor.split("\\R",-1)) {
            String resto=linea.strip();
            while (resto.length()>104) {
                int corte=resto.lastIndexOf(' ',104);
                if (corte<25) corte=104;
                lineas.add(resto.substring(0,corte));
                resto=resto.substring(corte).stripLeading();
            }
            lineas.add(resto);
        }
        return lineas;
    }

    private String unir(List<?> fila) {
        return String.join("  |  ",fila.stream().map(e -> Objects.toString(e,"")).toList());
    }

    public byte[] excel(Tabla tabla) {
        try (XSSFWorkbook libro=new XSSFWorkbook(); ByteArrayOutputStream salida=new ByteArrayOutputStream()) {
            XSSFSheet hoja=libro.createSheet("Reporte");
            CellStyle cabecera=libro.createCellStyle();
            Font fuente=libro.createFont();
            fuente.setBold(true);
            cabecera.setFont(fuente);
            int fila=0;
            hoja.createRow(fila++).createCell(0).setCellValue(tabla.titulo());
            for (String linea : tabla.resumen()) hoja.createRow(fila++).createCell(0).setCellValue(linea);
            Row encabezado=hoja.createRow(fila++);
            for (int i=0;i<tabla.columnas().size();i++) {
                Cell celda=encabezado.createCell(i);
                celda.setCellValue(tabla.columnas().get(i));
                celda.setCellStyle(cabecera);
                hoja.setColumnWidth(i,24*256);
            }
            hoja.createFreezePane(0,fila);
            for (List<?> registro : tabla.filas()) {
                Row renglon=hoja.createRow(fila++);
                for (int i=0;i<registro.size();i++) {
                    Cell celda=renglon.createCell(i);
                    Object valor=registro.get(i);
                    if (valor instanceof Number numero) celda.setCellValue(numero.doubleValue());
                    else celda.setCellValue(Objects.toString(valor,""));
                }
            }
            if (!tabla.serie().isEmpty()) graficoExcel(libro,tabla.serie());
            libro.write(salida);
            return salida.toByteArray();
        } catch (IOException error) { throw new UncheckedIOException(error); }
    }

    private void graficoExcel(XSSFWorkbook libro,Map<String,? extends Number> serie) {
        XSSFSheet hoja=libro.createSheet("Evolución diaria");
        Row encabezado=hoja.createRow(0);
        encabezado.createCell(0).setCellValue("Fecha");
        encabezado.createCell(1).setCellValue("Valor");
        int fila=1;
        for (var valor : serie.entrySet()) {
            Row renglon=hoja.createRow(fila++);
            renglon.createCell(0).setCellValue(valor.getKey());
            renglon.createCell(1).setCellValue(valor.getValue().doubleValue());
        }
        hoja.setColumnWidth(0,17*256);
        XSSFDrawing dibujo=hoja.createDrawingPatriarch();
        XSSFChart grafico=dibujo.createChart(dibujo.createAnchor(0,0,0,0,3,1,15,20));
        grafico.setTitleText("Evolución diaria");
        XDDFCategoryAxis x=grafico.createCategoryAxis(AxisPosition.BOTTOM);
        XDDFValueAxis y=grafico.createValueAxis(AxisPosition.LEFT);
        XDDFChartData datos=grafico.createData(ChartTypes.LINE,x,y);
        datos.addSeries(XDDFDataSourcesFactory.fromStringCellRange(hoja,new CellRangeAddress(1,fila-1,0,0)),
            XDDFDataSourcesFactory.fromNumericCellRange(hoja,new CellRangeAddress(1,fila-1,1,1))).setTitle("Valor",null);
        grafico.plot(datos);
    }

    public byte[] csv(Tabla tabla) {
        StringBuilder texto=new StringBuilder("\uFEFF");
        texto.append(lineaCsv(tabla.columnas()));
        for (List<?> fila : tabla.filas()) texto.append(lineaCsv(fila));
        return texto.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String lineaCsv(List<?> valores) {
        return String.join(",",valores.stream().map(v -> {
            String valor=Objects.toString(v,"");
            if (v instanceof Instant fecha) valor=fecha.toString();
            if (valor.stripLeading().matches("(?s)^[=+@-].*") || valor.startsWith("\t") || valor.startsWith("\r"))
                valor="'"+valor;
            return "\""+valor.replace("\"","\"\"")+"\"";
        }).toList())+"\r\n";
    }
}
