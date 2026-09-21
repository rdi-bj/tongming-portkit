package com.jinw.web.util;

import com.jinw.web.domain.IssueItem;
import com.jinw.web.domain.ReportData;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFHeader;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.xmlbeans.XmlCursor;
import org.apache.xmlbeans.XmlObject;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

    public class WordReportGenerator {

    private static final String TEMPLATE_RESOURCE = "/office/template.docx";

    private static final String W_NS = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    private static final String XML_NS = "http://www.w3.org/XML/1998/namespace";

    private static final String ISSUE_TABLE_FONT = "宋体";
    private static final BigInteger ISSUE_TABLE_SIZE_HALF_POINTS = BigInteger.valueOf(18);


    // ==================== 主方法 ====================

    public static void main(String[] args) throws Exception {
        ReportData data = mockData();
        generate("./temp/RISC-V跨平台移植检测报告.docx", data);
        System.out.println("报告已生成");
    }

    private static ReportData mockData() {
        return new ReportData()
                .setAiAdapted(true)
                .setIssues(List.of(
                        new IssueItem(1,
                                "\"__i386__\" 是编译器预定义的宏，用于判断当前编译目标是否为 x86 32位（i386）架构，通常用于实现与特定硬件平台相关的条件编译。",
                                "建议：添加 #elif defined(__riscv) && __riscv_xlen == 32，以适配 RISC‑V 32 位架构的条件编译。",
                                "proot-5.4.0/test/ptrace-2.c", 129),
                        new IssueItem(2,
                                "\"__x86_64__\" 宏用于 x86 64位架构的条件编译，当前缺少 RISC-V 64位分支。",
                                "建议：添加 #elif defined(__riscv) && __riscv_xlen == 64，以适配 RISC‑V 64 位架构。",
                                "proot-5.4.0/src/arch.c", 87),
                        new IssueItem(3,
                                "当前文件缺失risc-v架构实现",
                                "将 i386 的 int $0x80软中断调用替换为 RISC‑V 的 ecall，按 RISC‑V Linux ABI 使用 a0–a5传参、a7存 syscall 号，并改用 .align/ .globl/ .type等 GNU as 伪指令配合 RV32/RV64 寄存器宽度与调用约定重写实现。",
                                "proot-5.4.0/src/loader/assembly.S", 45)
                ));
    }

    // ==================== 生成逻辑 ====================

    public static void generate(String outputPath, ReportData data) throws Exception {
        try (FileOutputStream out = new FileOutputStream(outputPath)) {
            generate(out, data);
        }
    }

    public static void generate(OutputStream out, ReportData data) throws Exception {
        try (InputStream in = WordReportGenerator.class.getResourceAsStream(TEMPLATE_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("模板资源不存在: " + TEMPLATE_RESOURCE);
            }
            XWPFDocument doc = new XWPFDocument(in);
            fillTables(doc, data);
            mergeFields(doc, data);
            markUpdateFieldsOnOpen(doc);
            doc.write(out);
        }
    }

    // ==================== 打开时更新域 ====================

    private static void markUpdateFieldsOnOpen(XWPFDocument doc) {
        CTSettings settings = doc.getSettings().getCTSettings();
        if (settings.isSetUpdateFields()) {
            settings.getUpdateFields().setVal(true);
            return;
        }
        CTOnOff updateFields = settings.addNewUpdateFields();
        updateFields.setVal(true);
        XmlCursor insert = updateFields.newCursor();
        XmlCursor target = settings.newCursor();
        target.toFirstContentToken();
        boolean placed = false;
        while (target.currentTokenType() != XmlCursor.TokenType.STARTDOC
                && target.currentTokenType() != XmlCursor.TokenType.ENDDOC) {
            if (target.isStart()) {
                if ("footnotePr".equals(target.getName().getLocalPart())) {
                    placed = true;
                    break;
                }
                target.toEndToken();
            }
            target.toNextToken();
        }
        if (!placed) {
            target.toEndToken();
        }
        insert.moveXml(target);
        insert.dispose();
        target.dispose();
    }

    // ==================== MERGEFIELD 邮件合并 ====================

    private static void mergeFields(XWPFDocument doc, ReportData d) throws Exception {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("subjectName", d.getSubjectName());
        values.put("reportDate", d.getReportDate());
        values.put("platformName", d.getPlatformName());
        values.put("scannedFiles", String.valueOf(d.getScannedFiles()));
        values.put("codeLines", String.valueOf(d.getCodeLines()));
        values.put("modifyFiles", String.valueOf(d.getModifyFiles()));
        values.put("modifyLocations", String.valueOf(d.getModifyLocations()));
        values.put("coveredRules", String.valueOf(d.getCoveredRules()));
        values.put("headerIssues", String.valueOf(d.getHeaderIssues()));
        values.put("headerPercent", d.getHeaderPercent());
        values.put("macroIssues", String.valueOf(d.getMacroIssues()));
        values.put("macroPercent", d.getMacroPercent());
        values.put("asmIssues", String.valueOf(d.getAsmIssues()));
        values.put("asmPercent", d.getAsmPercent());
        values.put("otherIssues", String.valueOf(d.getOtherIssues()));
        values.put("otherPercent", d.getOtherPercent());
        values.put("conclusionArchIssues", String.valueOf(d.getConclusionArchIssues()));
        values.put("conclusionAsmIssues", String.valueOf(d.getConclusionAsmIssues()));
        values.put("conclusionBuildIssues", String.valueOf(d.getConclusionBuildIssues()));
        values.put("conclusionOtherIssues", String.valueOf(d.getConclusionOtherIssues()));
        values.put("coreSrcFiles", String.valueOf(d.getCoreSrcFiles()));
        values.put("coreSrcLines", String.valueOf(d.getCoreSrcLines()));
        values.put("coreSrcPercent", d.getCoreSrcPercent());
        values.put("coreAsmFiles", String.valueOf(d.getCoreAsmFiles()));
        values.put("coreAsmLines", String.valueOf(d.getCoreAsmLines()));
        values.put("coreAsmPercent", d.getCoreAsmPercent());
        values.put("coreBuildFiles", String.valueOf(d.getCoreBuildFiles()));
        values.put("coreBuildLines", String.valueOf(d.getCoreBuildLines()));
        values.put("coreBuildPercent", d.getCoreBuildPercent());

        // 基本信息表
        values.put("language", d.getLanguage());
        values.put("systemName", d.getSystemName());
        values.put("buildSystem", d.getBuildSystem());
        values.put("totalFiles", d.getTotalFiles());
        values.put("totalLines", d.getTotalLines());

        // 检测情况表
        values.put("srcFileCount", String.valueOf(d.getSrcFileCount()));
        values.put("srcFileLines", String.valueOf(d.getSrcFileLines()));
        values.put("asmFileCount", String.valueOf(d.getAsmFileCount()));
        values.put("asmFileLines", String.valueOf(d.getAsmFileLines()));
        values.put("buildFileCount", String.valueOf(d.getBuildFileCount()));
        values.put("buildFileLines", String.valueOf(d.getBuildFileLines()));
        values.put("otherFileCount", String.valueOf(d.getOtherFileCount()));
        values.put("otherFileLines", String.valueOf(d.getOtherFileLines()));

        String xml = doc.getDocument().getBody().xmlText();
        doc.getDocument().getBody().set(
                org.apache.xmlbeans.XmlObject.Factory.parse(mergeXmlFields(xml, values)));

        for (XWPFHeader header : doc.getHeaderList()) {
            CTHdrFtr hdr = header._getHdrFtr();
            hdr.set(org.apache.xmlbeans.XmlObject.Factory.parse(mergeXmlFields(hdr.xmlText(), values)));
        }
    }

    /**
     * 查找所有 MERGEFIELD 字段，将结果 run 文本替换为合并值（保留原 run 格式），
     * 然后移除 begin/instrText/separate/end 包裹 run，得到纯文本结果。
     */
    private static String mergeXmlFields(String xml, Map<String, String> values) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        Document dom = dbf.newDocumentBuilder().parse(new org.xml.sax.InputSource(new StringReader(xml)));

        XPath xpath = XPathFactory.newInstance().newXPath();
        XPathExpression expr = xpath.compile(
                "//*[local-name()='instrText' and starts-with(normalize-space(.), 'MERGEFIELD')]");
        NodeList list = (NodeList) expr.evaluate(dom, XPathConstants.NODESET);

        List<Element> fields = new ArrayList<>();
        for (int i = 0; i < list.getLength(); i++) {
            fields.add((Element) list.item(i));
        }

        for (Element instr : fields) {
            String name = instr.getTextContent().replace("MERGEFIELD", "").trim();
            String value = values.get(name);
            if (value == null) continue;

            Element instrRun = (Element) instr.getParentNode();
            Element para = (Element) instrRun.getParentNode();

            Element begin = prevElement(instrRun);
            Element separate = nextElement(instrRun);
            if (!isFldCharRun(begin, "begin") || !isFldCharRun(separate, "separate")) continue;

            Element result = nextElement(separate);
            Element end = result == null ? null : nextElement(result);
            if (result == null || !"w:r".equals(result.getNodeName()) || !isFldCharRun(end, "end")) continue;

            Element t = firstChildByLocalName(result, "t");
            if (t == null) {
                t = dom.createElementNS(W_NS, "w:t");
                result.appendChild(t);
            }
            t.setTextContent(value);
            t.setAttributeNS(XML_NS, "xml:space", "preserve");

            para.removeChild(begin);
            para.removeChild(instrRun);
            para.removeChild(separate);
            para.removeChild(end);
        }

        Transformer tf = TransformerFactory.newInstance().newTransformer();
        StringWriter sw = new StringWriter();
        tf.transform(new DOMSource(dom), new StreamResult(sw));
        return sw.toString();
    }

    private static Element prevElement(Node node) {
        Node n = node.getPreviousSibling();
        while (n != null && n.getNodeType() != Node.ELEMENT_NODE) {
            n = n.getPreviousSibling();
        }
        return (Element) n;
    }

    private static Element nextElement(Node node) {
        Node n = node.getNextSibling();
        while (n != null && n.getNodeType() != Node.ELEMENT_NODE) {
            n = n.getNextSibling();
        }
        return (Element) n;
    }

    private static boolean isFldCharRun(Element run, String type) {
        if (run == null || !"w:r".equals(run.getNodeName())) return false;
        Element fc = firstChildByLocalName(run, "fldChar");
        return fc != null && type.equals(fc.getAttributeNS(W_NS, "fldCharType"));
    }

    private static Element firstChildByLocalName(Element parent, String localName) {
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n.getNodeType() == Node.ELEMENT_NODE && localName.equals(((Element) n).getLocalName())) {
                return (Element) n;
            }
        }
        return null;
    }

    // ==================== 表格填充 ====================

    private static void fillTables(XWPFDocument doc, ReportData d) {
        List<XWPFTable> tables = doc.getTables();
        if (tables.size() < 4) return;

        XWPFTable blockTable = tables.get(2);
        XWPFTable simpleTable = tables.get(3);

        // 检测后无数据时，直接隐藏附件1（问题明细标题及明细表）
        if (d.getIssues() == null || d.getIssues().isEmpty()) {
            removeAttachmentSection(doc, blockTable, simpleTable);
            return;
        }

        if (d.isAiAdapted()) {
            fillIssueTable(blockTable, d.getIssues());
            applyIssueTableFont(blockTable);
            removeTable(doc, simpleTable);
        } else {
            fillSimpleIssueTable(simpleTable, d.getIssues());
            applyIssueTableFont(simpleTable);
            removeTable(doc, blockTable);
        }
    }

    /** 无检测数据时移除附件1：标题段落、其前的分页段落以及两个明细表。 */
    private static void removeAttachmentSection(XWPFDocument doc, XWPFTable blockTable, XWPFTable simpleTable) {
        List<Integer> positions = new ArrayList<>();
        int blockPos = doc.getPosOfTable(blockTable);
        int simplePos = doc.getPosOfTable(simpleTable);
        if (blockPos >= 0) positions.add(blockPos);
        if (simplePos >= 0) positions.add(simplePos);

        for (XWPFParagraph para : doc.getParagraphs()) {
            String text = para.getText();
            if (text != null && text.contains("附件1")) {
                int headingPos = doc.getPosOfParagraph(para);
                positions.add(headingPos);
                if (headingPos > 0) {
                    positions.add(headingPos - 1);
                }
            }
        }

        positions.sort(Collections.reverseOrder());
        for (int pos : positions) {
            doc.removeBodyElement(pos);
        }
    }

    /** 统一问题列表表格内所有 run 的字体与字号，避免表头/序号与正文内容字号不一致。 */
    private static void applyIssueTableFont(XWPFTable table) {
        CTTbl tbl = table.getCTTbl();
        for (CTRow row : tbl.getTrArray()) {
            for (CTTc tc : row.getTcArray()) {
                for (CTP p : tc.getPArray()) {
                    for (CTR r : p.getRArray()) {
                        CTRPr rPr = r.isSetRPr() ? r.getRPr() : r.addNewRPr();
                        CTFonts fonts = rPr.sizeOfRFontsArray() > 0 ? rPr.getRFontsArray(0) : rPr.addNewRFonts();
                        fonts.setAscii(ISSUE_TABLE_FONT);
                        fonts.setHAnsi(ISSUE_TABLE_FONT);
                        fonts.setEastAsia(ISSUE_TABLE_FONT);
                        fonts.setCs(ISSUE_TABLE_FONT);
                        CTHpsMeasure sz = rPr.sizeOfSzArray() > 0 ? rPr.getSzArray(0) : rPr.addNewSz();
                        sz.setVal(ISSUE_TABLE_SIZE_HALF_POINTS);
                        CTHpsMeasure szCs = rPr.sizeOfSzCsArray() > 0 ? rPr.getSzCsArray(0) : rPr.addNewSzCs();
                        szCs.setVal(ISSUE_TABLE_SIZE_HALF_POINTS);
                    }
                }
            }
        }
    }

    private static void removeTable(XWPFDocument doc, XWPFTable table) {
        int pos = doc.getPosOfTable(table);
        if (pos >= 0) {
            doc.removeBodyElement(pos);
        }
    }

    /** 填充 AI 适配后的明细表（块格式）；无问题时返回 false。每条问题一个块：
     *  表头行(序号|问题描述|修改意见) + 3 行内容（描述/建议、文件名、代码行），序号列垂直合并。 */
    private static boolean fillIssueTable(XWPFTable table, List<IssueItem> issues) {
        if (issues == null || issues.isEmpty()) return false;
        CTTbl tbl = table.getCTTbl();
        int row = 0;
        for (IssueItem item : issues) {
            row = fillIssueBlock(tbl, row, item);
        }
        trimTrailingRows(tbl, row);
        return true;
    }

    /** 填充非 AI 适配后的明细表（单行格式：序号|文件名|代码行|问题描述）。 */
    private static boolean fillSimpleIssueTable(XWPFTable table, List<IssueItem> issues) {
        if (issues == null || issues.isEmpty()) return false;
        CTTbl tbl = table.getCTTbl();
        int row = 1;
        for (IssueItem item : issues) {
            row = fillSimpleIssueRow(tbl, row, item);
        }
        trimTrailingRows(tbl, row);
        return true;
    }

    private static int fillSimpleIssueRow(CTTbl tbl, int row, IssueItem item) {
        row = ensureRowXml(tbl, row, 1);
        CTRow r = tbl.getTrArray(row);
        setCellTextXml(r, 0, String.valueOf(item.getIndex()));
        setCellTextXml(r, 1, item.getFileName());
        setCellTextXml(r, 2, String.valueOf(item.getLineNumber()));
        setCellTextXml(r, 3, item.getDescription());
        return ++row;
    }

    /** 从 row 开始写入一条问题（表头行 + 3 行内容），返回下一行下标。 */
    private static int fillIssueBlock(CTTbl tbl, int row, IssueItem item) {
        row = ensureRowXml(tbl, row, 0);
        CTRow head = tbl.getTrArray(row);
        setVMerge(head, 0, "restart");
        setCellTextXml(head, 0, String.valueOf(item.getIndex()));
        setCellTextXml(head, 1, "问题描述");
        setCellTextXml(head, 2, "修改意见");

        row = ensureRowXml(tbl, ++row, 1);
        CTRow desc = tbl.getTrArray(row);
        setVMerge(desc, 0, "continue");
        setCellTextXml(desc, 0, "");
        setCellTextXml(desc, 1, item.getDescription());
        setCellTextXml(desc, 2, item.getSuggestion());

        row = ensureRowXml(tbl, ++row, 2);
        CTRow file = tbl.getTrArray(row);
        setVMerge(file, 0, "continue");
        setCellTextXml(file, 0, "");
        setCellTextXml(file, 1, "文件名");
        setCellTextXml(file, 2, item.getFileName());

        row = ensureRowXml(tbl, ++row, 3);
        CTRow line = tbl.getTrArray(row);
        setVMerge(line, 0, "continue");
        setCellTextXml(line, 0, "");
        setCellTextXml(line, 1, "代码行");
        setCellTextXml(line, 2, String.valueOf(item.getLineNumber()));

        return ++row;
    }

    /** 移除行数多于问题块所需的多余行（模板残留行）。 */
    private static void trimTrailingRows(CTTbl tbl, int keepRows) {
        while (tbl.sizeOfTrArray() > keepRows) {
            tbl.removeTr(tbl.sizeOfTrArray() - 1);
        }
    }

    private static void setVMerge(CTRow row, int colIdx, String val) {
        CTTc tc = row.getTcArray(colIdx);
        CTTcPr tcPr = tc.isSetTcPr() ? tc.getTcPr() : tc.addNewTcPr();
        CTVMerge vm = tcPr.isSetVMerge() ? tcPr.getVMerge() : tcPr.addNewVMerge();
        vm.setVal(STMerge.Enum.forString(val));
    }

    /** 行数不足时按模板行克隆追加（列宽、样式一致），返回合法行下标。 */
    private static int ensureRowXml(CTTbl tbl, int rowIdx, int templateRowIdx) {
        while (rowIdx >= tbl.sizeOfTrArray()) {
            int src = Math.min(templateRowIdx, tbl.sizeOfTrArray() - 1);
            CTRow copy = (CTRow) tbl.getTrArray(src).copy();
            stripParaIds(copy);
            tbl.addNewTr().set(copy);
        }
        return rowIdx;
    }

    /** 将单元格首个段落的文本整体替换为 value（清掉旧 run，仅保留首个 run 的格式）。 */
    private static void setCellTextXml(CTRow row, int colIdx, String value) {
        CTTc[] tcs = row.getTcArray();
        if (colIdx >= tcs.length) return;
        CTP[] paras = tcs[colIdx].getPArray();
        if (paras.length == 0) return;
        CTP p = paras[0];
        CTR[] runs = p.getRArray();
        if (runs.length == 0) {
            p.addNewR().addNewT().setStringValue(value);
            return;
        }
        for (int i = runs.length - 1; i > 0; i--) {
            p.removeR(i);
        }
        CTR r0 = p.getRArray(0);
        while (r0.sizeOfTArray() > 0) r0.removeT(0);
        r0.addNewT().setStringValue(value);
    }

    /** 去掉克隆行中重复的 w14:paraId 属性，避免 Word/WPS 校验告警。 */
    private static final javax.xml.namespace.QName PARA_ID_QNAME =
            new javax.xml.namespace.QName("http://schemas.microsoft.com/office/word/2010/wordml", "paraId");

    private static void stripParaIds(CTRow row) {
        XmlCursor c = row.newCursor();
        if (c.getAttributeText(PARA_ID_QNAME) != null) {
            c.removeAttribute(PARA_ID_QNAME);
        }
        c.dispose();
        for (XmlObject el : row.selectPath(
                "declare namespace w='" + W_NS + "' .//w:tc | .//w:p | .//w:tr")) {
            XmlCursor cc = el.newCursor();
            if (cc.getAttributeText(PARA_ID_QNAME) != null) {
                cc.removeAttribute(PARA_ID_QNAME);
            }
            cc.dispose();
        }
    }

    // ==================== 工具方法 ====================
}
