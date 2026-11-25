package co.nexoeventos.dominio;


public record QrEntrada(String evento,String compra,long fecha,String entrada) {
    private static final String PREFIJO="NEXO1|";

    public static String generar(Compra compra,String codigo) {
        return PREFIJO+compra.eventoId+"|"+compra.id+"|"+compra.pago.fecha().toEpochMilli()+"|"+codigo;
    }

    public static boolean esQr(String valor) { return valor.startsWith(PREFIJO); }

    public static QrEntrada leer(String valor) {
        String[] partes=valor.split("\\|",-1);
        if (partes.length!=5) return null;
        try { return new QrEntrada(partes[1],partes[2],Long.parseLong(partes[3]),partes[4]); }
        catch (NumberFormatException error) { return null; }
    }

    public boolean corresponde(Compra candidata) {
        return candidata!=null && candidata.pago!=null && evento.equals(candidata.eventoId)
            && compra.equals(candidata.id) && fecha==candidata.pago.fecha().toEpochMilli();
    }
}
