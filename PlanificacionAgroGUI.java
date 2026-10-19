import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.util.*;
import java.util.List;

public class PlanificacionAgroGUI extends JFrame {

    // LISTAS

    // Lista Simple 
    static class NodoAdyacencia { 
        int destino, peso; 
        NodoAdyacencia siguiente; 
        NodoAdyacencia(int d, int p) { destino = d; peso = p; } 
    }
    
    // Lista Doble 
    static class NodoDoble { 
        int tarea; 
        NodoDoble anterior, siguiente; 
        NodoDoble(int t) { tarea = t; } 
    }
    
    static class ListaDoble {
        NodoDoble cabeza;
        void agregar(int t) {
            NodoDoble n = new NodoDoble(t);
            if (cabeza == null) { cabeza = n; return; }
            NodoDoble a = cabeza;
            while (a.siguiente != null) a = a.siguiente;
            a.siguiente = n; 
            n.anterior = a;
        }
    }
    
    // Lista Circular 
    static class Camion { 
        String placa; 
        int capacidad; 
        Camion siguiente; 
        Camion(String p, int c) { placa = p; capacidad = c; } 
    }
    
    static class ListaCircularCamiones {
        Camion ultimo; 
        int tamaño;
        void agregar(String placa, int cap) {
            Camion n = new Camion(placa, cap);
            if (ultimo == null) n.siguiente = n;
            else { 
                n.siguiente = ultimo.siguiente; 
                ultimo.siguiente = n; 
            }
            ultimo = n; 
            tamaño++;
        }
        String mostrarTurnos(int vueltas) {
            if (ultimo == null) return "Sin camiones.";
            StringBuilder sb = new StringBuilder(); 
            Camion a = ultimo.siguiente;
            for (int i = 0; i < tamaño * vueltas; i++, a = a.siguiente) 
                sb.append(String.format("Turno %2d -> Camión %s (cap: %d ton)%n", i + 1, a.placa, a.capacidad));
            return sb.toString();
        }
    }

    // MATRICES (2D)
    static int[][] matrizTiempos = { 
        {4, 6, 3}, 
        {5, 4, 7}, 
        {3, 8, 5} 
    };
    
    static double[][] matrizRendimiento = { 
        {12.5, 10.2, 9.8}, 
        {11.0, 13.4, 8.5}, 
        {10.5, 9.9, 12.0} 
    };

    // VECTORES (1D)
    static int[] inicioTemprano, duracionTarea;
    static String[] nombres = {"Cosecha", "Transporte", "Limpieza", "Empaque", "Almacenaje"};
    static int[] ordenPresentacion = {0, 2, 1, 3, 4};
    static NodoAdyacencia[] grafo;
    static ListaDoble[] predecesores;
    static int numTareas = 5;

    // PILA
    static class Pila {
        int[] d; 
        int tope = -1;
        Pila(int n) { d = new int[n]; }
        void push(int v) { d[++tope] = v; } 
        int pop() { return d[tope--]; } 
        boolean vacia() { return tope == -1; }
    }

    // COLA
    static class Cola {
        int[] d; 
        int f = 0, fin = -1, tam = 0, cap;
        Cola(int c) { cap = c; d = new int[c]; }
        void encolar(int v) { fin = (fin + 1) % cap; d[fin] = v; tam++; }
        int desencolar() { int v = d[f]; f = (f + 1) % cap; tam--; return v; }
        boolean vacia() { return tam == 0; }
    }

    // INGRESAR TIEMPOS DE RECOLECCIÓN
    private void ingresarTiemposRecoleccion() {
        JPanel panel = new JPanel(new GridLayout(4, 4, 8, 8)); 
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        panel.add(new JLabel("")); 
        for (int j = 0; j < 3; j++) panel.add(celda("Lote " + j));
        
        JTextField[][] campos = new JTextField[3][3];
        for (int i = 0; i < 3; i++) {
            panel.add(celda("Finca " + i));
            for (int j = 0; j < 3; j++) { 
                campos[i][j] = new JTextField(String.valueOf(matrizTiempos[i][j]), 5); 
                campos[i][j].setHorizontalAlignment(JTextField.CENTER); 
                panel.add(campos[i][j]); 
            }
        }
        if (JOptionPane.showConfirmDialog(this, panel, "Ingresar tiempos de recolección (horas)", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
        
        try {
            for (int i = 0; i < 3; i++) 
                for (int j = 0; j < 3; j++) { 
                    int val = Integer.parseInt(campos[i][j].getText().trim()); 
                    if (val < 0) throw new NumberFormatException(); 
                    matrizTiempos[i][j] = val; 
                }
            mostrarTiemposRecoleccion(); 
            etiquetaEstado.setText("Tiempos de recolección actualizados correctamente.");
        } catch (NumberFormatException ex) { 
            JOptionPane.showMessageDialog(this, "Debe ingresar números enteros positivos.", "Error de entrada", JOptionPane.ERROR_MESSAGE); 
        }
    }
    
    private void mostrarTiemposRecoleccion() {
        StringBuilder sb = new StringBuilder("=== MATRIZ 2D: TIEMPOS DE RECOLECCIÓN (HORAS) ===\n\n");
        sb.append(String.format("%-10s %-8s %-8s %-8s %-8s%n", "Finca", "Lote 0", "Lote 1", "Lote 2", "Total"))
          .append("--------------------------------------------------\n");
        for (int i = 0; i < matrizTiempos.length; i++) { 
            int total = 0; 
            for (int v : matrizTiempos[i]) total += v; 
            sb.append(String.format("%-10s %-8d %-8d %-8d %-8d%n", "Finca " + i, matrizTiempos[i][0], matrizTiempos[i][1], matrizTiempos[i][2], total)); 
        }
        salida(sb.toString(), "Mostrando tiempos de recolección actuales.");
    }

    // FUNCIONALIDAD: RENDIMIENTO DE LAS TAREAS
    private static final double UMBRAL_RENDIMIENTO = 11.5;
    private static final String[] EST_REND = {" Óptimo", "  Aceptable", " Deficiente"};
    
    static double promedio(double[] fila) { 
        double s = 0; 
        for (double v : fila) s += v; 
        return s / fila.length; 
    }
    
    static int nivelRend(double p) { return p >= UMBRAL_RENDIMIENTO ? 0 : p >= UMBRAL_RENDIMIENTO * 0.9 ? 1 : 2; }
    
    private void mostrarRendimiento() {
        for (double[] fila : matrizRendimiento) 
            for (int j = 0; j < fila.length; j++) 
                fila[j] = 8.0 + Math.round(RND.nextDouble() * 70) / 10.0;
        imprimirTablaRendimiento("(valores regenerados aleatoriamente)"); 
        etiquetaEstado.setText("Rendimiento regenerado con nuevos valores aleatorios.");
    }
    
    private void verRendimientoActual() { 
        imprimirTablaRendimiento("(últimos valores generados)"); 
        etiquetaEstado.setText("Mostrando el rendimiento actual (sin regenerar)."); 
    }
    
    private void imprimirTablaRendimiento(String sub) {
        StringBuilder sb = new StringBuilder("=== RENDIMIENTO DE LAS TAREAS (ton/ha) ===\n").append(sub).append("\n\n");
        sb.append(String.format("%-10s %-10s %-10s %-10s %-10s %-25s%n", "Finca", "Lote 0", "Lote 1", "Lote 2", "Promedio", "Estado"))
          .append("------------------------------------------------------------------------------------\n");
        for (int i = 0; i < matrizRendimiento.length; i++) {
            double p = promedio(matrizRendimiento[i]);
            sb.append(String.format("%-10s %-10.2f %-10.2f %-10.2f %-10.2f %-25s%n", "Finca " + i, matrizRendimiento[i][0], matrizRendimiento[i][1], matrizRendimiento[i][2], p, EST_REND[nivelRend(p)]));
        }
        sb.append("\n=== ESTÁNDAR DE REFERENCIA ===\n").append(String.format("Umbral fijo de rendimiento: %.2f ton/ha%n", UMBRAL_RENDIMIENTO));
        sb.append("• Promedio ≥ umbral      ->  Óptimo\n• Promedio ≥ 90% umbral  ->  Aceptable\n• Promedio < 90% umbral  ->  Deficiente\n");
        areaSalida.setText(sb.toString());
    }

    // FUNCIONALIDAD: TIEMPOS DE INICIO Y FIN
    private static final int UMBRAL_DURACION_TAREA = 4;
    private static final String[] EST_DUR = {" Eficiente", "  Aceptable", " Lenta"};
    
    static void inicializarGrafo() {
        grafo = new NodoAdyacencia[numTareas]; 
        predecesores = new ListaDoble[numTareas]; 
        duracionTarea = new int[numTareas];
        for (int i = 0; i < numTareas; i++) predecesores[i] = new ListaDoble();
    }
    
    static void agregarArista(int o, int d, int p) { 
        NodoAdyacencia n = new NodoAdyacencia(d, p); 
        n.siguiente = grafo[o]; 
        grafo[o] = n; 
        predecesores[d].agregar(o); 
    }
    
    static List<Integer> ordenTopologico() {
        int[] ge = new int[numTareas]; 
        for (int i = 0; i < numTareas; i++) 
            for (NodoAdyacencia a = grafo[i]; a != null; a = a.siguiente) ge[a.destino]++;
            
        Pila p = new Pila(numTareas); 
        for (int i = 0; i < numTareas; i++) if (ge[i] == 0) p.push(i);
        
        List<Integer> orden = new ArrayList<>();
        while (!p.vacia()) { 
            int u = p.pop(); 
            orden.add(u); 
            for (NodoAdyacencia a = grafo[u]; a != null; a = a.siguiente) 
                if (--ge[a.destino] == 0) p.push(a.destino); 
        }
        return orden;
    }
    
    static void calcularTiempos() {
        inicioTemprano = new int[numTareas];
        for (int u : ordenTopologico()) { 
            int finU = inicioTemprano[u] + duracionTarea[u]; 
            for (NodoAdyacencia a = grafo[u]; a != null; a = a.siguiente) 
                if (finU > inicioTemprano[a.destino]) inicioTemprano[a.destino] = finU; 
        }
    }
    
    static int nivelDur(int d) { return d <= UMBRAL_DURACION_TAREA ? 0 : d == UMBRAL_DURACION_TAREA + 1 ? 1 : 2; }
    
    private void mostrarInicioFinConCambio() {
        for (int i = 0; i < numTareas; i++) duracionTarea[i] = 1 + RND.nextInt(6);
        imprimirTablaInicioFin("(duraciones regeneradas aleatoriamente)"); 
        etiquetaEstado.setText("Tiempos de inicio/fin regenerados con nuevas duraciones.");
    }
    
    private void verInicioFinActual() { 
        imprimirTablaInicioFin("(últimos valores generados)"); 
        etiquetaEstado.setText("Mostrando tiempos de inicio/fin actuales (sin regenerar)."); 
    }
    
    private void imprimirTablaInicioFin(String sub) {
        calcularTiempos(); 
        int[] inicio = new int[numTareas], fin = new int[numTareas]; 
        int hora = 0;
        for (int idx : ordenPresentacion) { 
            inicio[idx] = hora; 
            fin[idx] = hora += duracionTarea[idx]; 
        }
        StringBuilder sb = new StringBuilder("=== TIEMPOS DE INICIO Y FIN POR TAREA ===\n").append(sub).append("\n\n");
        sb.append(String.format("%-12s %-10s %-10s %-10s %-35s%n", "Tarea", "Inicio (h)", "Fin (h)", "Duración", "Análisis"))
          .append("------------------------------------------------------------------------------------\n");
        for (int idx : ordenPresentacion) 
            sb.append(String.format("%-12s %-10d %-10d %-10d %-35s%n", nombres[idx], inicio[idx], fin[idx], duracionTarea[idx], EST_DUR[nivelDur(duracionTarea[idx])]));
        sb.append("\n=== OBSERVACIONES ===\n• Duración total del proceso: ").append(hora).append(" h\n\n=== ESTÁNDAR DE REFERENCIA ===\n")
          .append(String.format("Umbral fijo de duración: %d h por tarea%n", UMBRAL_DURACION_TAREA));
        sb.append("• Duración ≤ umbral       ->  Eficiente\n• Duración = umbral + 1   ->  Aceptable\n• Duración > umbral + 1   ->  Lenta\n");
        areaSalida.setText(sb.toString());
    }

    // FUNCIONALIDAD: TURNOS DE LOS CAMIONES
    private void pedirTurnosCamiones() {
        String input = JOptionPane.showInputDialog(this, "¿Cuántos turnos de camiones desea mostrar?", "Turnos de camiones", JOptionPane.QUESTION_MESSAGE);
        if (input == null) return;
        try {
            int t = Integer.parseInt(input.trim()); 
            if (t <= 0) throw new NumberFormatException();
            ListaCircularCamiones lc = new ListaCircularCamiones(); 
            lc.agregar("ABC-123", 10); 
            lc.agregar("DEF-456", 12); 
            lc.agregar("GHI-789", 8);
            salida("=== LISTA CIRCULAR: TURNOS DE CAMIONES ===\n\n" + lc.mostrarTurnos(t) + "\n(Los turnos se repiten de forma circular)\n", "Mostrando " + t + " turno(s) de camiones.");
        } catch (NumberFormatException ex) { 
            JOptionPane.showMessageDialog(this, "Ingrese un número entero mayor a 0.", "Error", JOptionPane.ERROR_MESSAGE); 
        }
    }

    // FUNCIONALIDAD: RECORRIDO BFS Y ANÁLISIS
    static String bfs(int inicio) {
        boolean[] vis = new boolean[numTareas]; 
        Cola c = new Cola(numTareas); 
        c.encolar(inicio); 
        vis[inicio] = true;
        StringBuilder sb = new StringBuilder("BFS desde " + nombres[inicio] + ":\n");
        while (!c.vacia()) { 
            int u = c.desencolar(); 
            sb.append("  -> ").append(nombres[u]).append("\n"); 
            for (NodoAdyacencia a = grafo[u]; a != null; a = a.siguiente) 
                if (!vis[a.destino]) { 
                    vis[a.destino] = true; 
                    c.encolar(a.destino); 
                } 
        }
        return sb.toString();
    }
    
    private void mostrarBFS() {
        StringBuilder sb = new StringBuilder("=== COLA: RECORRIDO BFS DEL GRAFO DE TAREAS ===\n\n").append(bfs(0));
        sb.append("\n").append(LINEA).append("    ANÁLISIS DE PLANIFICACIÓN - EVITAR PÉRDIDAS\n").append(LINEA).append("\n");
        
        calcularTiempos(); 
        int total = 0; 
        for (int i = 0; i < numTareas; i++) 
            total = Math.max(total, inicioTemprano[i] + duracionTarea[i]);
            
        sb.append("  DURACIÓN TOTAL DEL PROCESO\n").append(String.format("    Tiempo estimado: %d horas%n", total));
        if (total <= 14) sb.append("     El proceso es rápido, la fruta no pierde frescura.\n");
        else if (total <= 20) sb.append("      Proceso aceptable, vigilar la cadena de frío.\n");
        else sb.append("     Proceso demasiado largo, riesgo alto de pérdida postcosecha.\n");
        
        sb.append("\n  TAREAS MÁS LARGAS (posibles cuellos de botella)\n");
        List<Integer> idxs = new ArrayList<>(); 
        for (int i = 0; i < numTareas; i++) idxs.add(i); 
        idxs.sort((a, b) -> duracionTarea[b] - duracionTarea[a]);
        
        int mostradas = 0; 
        for (int i : idxs) 
            if (duracionTarea[i] > UMBRAL_DURACION_TAREA) { 
                sb.append(String.format("     %-12s -> %d h (supera el estándar)%n", nombres[i], duracionTarea[i])); 
                mostradas++; 
            }
            
        if (mostradas == 0) sb.append("     Ninguna tarea supera el estándar de duración.\n"); 
        else sb.append("     Revisar estas tareas: podrían retrasar todo el proceso.\n");
        
        sb.append("\n  ESTADO DEL RENDIMIENTO (última matriz)\n");
        int[] cont = new int[3]; 
        for (double[] f : matrizRendimiento) cont[nivelRend(promedio(f))]++;
        sb.append(String.format("     Fincas óptimas:      %d%n", cont[0]))
          .append(String.format("     Fincas aceptables:   %d%n", cont[1]))
          .append(String.format("     Fincas deficientes:  %d%n", cont[2]));
          
        if (cont[2] > 0) sb.append("     Priorizar revisión agronómica en fincas deficientes.\n");
        
        sb.append("\n  RECOMENDACIONES PARA EVITAR PÉRDIDAS\n");
        if (cont[2] > 0) sb.append("    • Revisar los lotes con bajo rendimiento antes de cosechar.\n");
        if (mostradas > 0) sb.append("    • Reducir la duración de las tareas más lentas.\n");
        if (total > 20) sb.append("    • El proceso es largo: considerar más camiones o turnos dobles.\n");
        if (cont[2] == 0 && mostradas == 0 && total <= 20) sb.append("     Planificación saludable. Mantener la cadena actual.\n");
        sb.append("    • Priorizar siempre las tareas más largas para no perder producto.\n");
        sb.append("\n").append(LINEA).append("   FIN DEL ANÁLISIS\n").append(LINEA);
        
        salida(sb.toString(), "Mostrando recorrido BFS y análisis de planificación.");
    }

    // INTERFAZ GRÁFICA (CONSTRUCTOR, PALETA Y MÉTODOS VISUALES)
    private static final Color COLOR_FONDO = new Color(0xF1, 0xF8, 0xE9), COLOR_PANEL = new Color(0xDC, 0xED, 0xC8), COLOR_TITULO = new Color(0x2E, 0x7D, 0x32), COLOR_AREA = new Color(0xFF, 0xFF, 0xF8), COLOR_ESTADO = new Color(0x33, 0x69, 0x1E);
    private static final Color COLOR_BTN_TIEMPOS = new Color(0x9C, 0x27, 0xB0), COLOR_BTN_RENDIMIENTO = new Color(0x66, 0xBB, 0x6A), COLOR_BTN_INICIOFIN = new Color(0x3F, 0x51, 0xB5), COLOR_BTN_CAMIONES = new Color(0xFF, 0xB7, 0x4D), COLOR_BTN_BFS = new Color(0x4F, 0xC3, 0xF7), COLOR_BTN_LIMPIAR = new Color(0xEF, 0x53, 0x50), COLOR_TEXTO_BTN = Color.WHITE;
    private static final String LINEA = "═══════════════════════════════════════════════════════════\n"; 
    private static final Random RND = new Random();
    
    private JTextArea areaSalida; 
    private JLabel etiquetaEstado;

    public PlanificacionAgroGUI() {
        super("Planificación Agro - Pérdidas Postcosecha"); 
        setDefaultCloseOperation(EXIT_ON_CLOSE); 
        setSize(900, 700); 
        setLocationRelativeTo(null); 
        setLayout(new BorderLayout(10, 10)); 
        getContentPane().setBackground(COLOR_FONDO);
        
        add(barra(" Planificación Agro: Evitar Pérdidas Postcosecha ", SwingConstants.CENTER, 17, COLOR_TITULO, 14, 10), BorderLayout.NORTH);
        
        JPanel panelBotones = new JPanel(new GridLayout(6, 2, 12, 12)); 
        panelBotones.setBackground(COLOR_PANEL); 
        panelBotones.setBorder(marco("Acciones", 15, 10));
        
        Object[][] acciones = {
            {"Ingresar tiempos de recolección", COLOR_BTN_TIEMPOS, (Runnable) this::ingresarTiemposRecoleccion}, 
            {"Ver tiempos actuales", COLOR_BTN_TIEMPOS, (Runnable) this::mostrarTiemposRecoleccion},
            {"Rendimiento de las tareas", COLOR_BTN_RENDIMIENTO, (Runnable) this::mostrarRendimiento}, 
            {"Ver rendimiento actual", COLOR_BTN_RENDIMIENTO, (Runnable) this::verRendimientoActual},
            {"Tiempos de inicio y fin", COLOR_BTN_INICIOFIN, (Runnable) this::mostrarInicioFinConCambio}, 
            {"Ver inicio/fin actual", COLOR_BTN_INICIOFIN, (Runnable) this::verInicioFinActual},
            {"Turnos de los camiones", COLOR_BTN_CAMIONES, (Runnable) this::pedirTurnosCamiones}, 
            {"Recorrido BFS (Cola)", COLOR_BTN_BFS, (Runnable) this::mostrarBFS},
            {"Limpiar pantalla", COLOR_BTN_LIMPIAR, (Runnable) () -> salida("", "Pantalla limpiada.")}
        };
        
        for (Object[] a : acciones) panelBotones.add(crearBoton((String) a[0], (Color) a[1], (Runnable) a[2]));
        for (int i = 0; i < 3; i++) panelBotones.add(new JLabel("")); 
        add(panelBotones, BorderLayout.WEST);
        
        areaSalida = new JTextArea(); 
        areaSalida.setEditable(false); 
        areaSalida.setFont(new Font("Monospaced", Font.PLAIN, 13)); 
        areaSalida.setBackground(COLOR_AREA); 
        areaSalida.setForeground(new Color(0x1B, 0x5E, 0x20));
        
        JScrollPane scroll = new JScrollPane(areaSalida); 
        scroll.setBorder(marco("Resultados", 5, 15)); 
        scroll.getViewport().setBackground(COLOR_AREA); 
        add(scroll, BorderLayout.CENTER);
        
        etiquetaEstado = barra("Listo. Seleccione una opción.", SwingConstants.LEADING, 12, COLOR_ESTADO, 8, 12); 
        add(etiquetaEstado, BorderLayout.SOUTH);
    }
    
    private static JLabel barra(String txt, int alin, int tam, Color fondo, int v, int h) {
        JLabel l = new JLabel(txt, alin); 
        l.setFont(new Font("SansSerif", Font.BOLD, tam)); 
        l.setForeground(COLOR_TEXTO_BTN); 
        l.setOpaque(true); 
        l.setBackground(fondo); 
        l.setBorder(BorderFactory.createEmptyBorder(v, h, v, h)); 
        return l;
    }
    
    private static Border marco(String titulo, int izq, int der) {
        return BorderFactory.createCompoundBorder(
            BorderFactory.createEmptyBorder(10, izq, 10, der), 
            BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(COLOR_TITULO, 2), 
                titulo, TitledBorder.LEFT, TitledBorder.TOP, 
                new Font("SansSerif", Font.BOLD, 12), COLOR_TITULO
            )
        );
    }
    
    private void salida(String texto, String estado) { 
        areaSalida.setText(texto); 
        etiquetaEstado.setText(estado); 
    }
    
    private JButton crearBoton(String texto, Color color, Runnable accion) {
        JButton b = new JButton(texto); 
        b.setFocusPainted(false); 
        b.setFont(new Font("SansSerif", Font.BOLD, 12)); 
        b.setForeground(COLOR_TEXTO_BTN); 
        b.setBackground(color); 
        b.setOpaque(true);
        b.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(color.darker(), 2), 
            BorderFactory.createEmptyBorder(8, 10, 8, 10)
        )); 
        b.setCursor(new Cursor(Cursor.HAND_CURSOR)); 
        b.addActionListener(e -> accion.run());
        b.addMouseListener(new java.awt.event.MouseAdapter() { 
            public void mouseEntered(java.awt.event.MouseEvent e) { b.setBackground(color.brighter()); } 
            public void mouseExited(java.awt.event.MouseEvent e) { b.setBackground(color); } 
        }); 
        return b;
    }
    
    private static JLabel celda(String texto) { 
        JLabel l = new JLabel(texto, SwingConstants.CENTER); 
        l.setFont(new Font("SansSerif", Font.BOLD, 12)); 
        l.setForeground(COLOR_TITULO); 
        return l; 
    }

    // MÉTODO PRINCIPAL (MAIN)
    public static void main(String[] args) {
        inicializarGrafo(); 
        System.arraycopy(new int[]{4, 2, 3, 5, 2}, 0, duracionTarea, 0, 5);
        agregarArista(0, 1, 2); 
        agregarArista(0, 2, 3); 
        agregarArista(1, 3, 5); 
        agregarArista(2, 3, 5); 
        agregarArista(3, 4, 2);
        SwingUtilities.invokeLater(() -> new PlanificacionAgroGUI().setVisible(true));
    }
}