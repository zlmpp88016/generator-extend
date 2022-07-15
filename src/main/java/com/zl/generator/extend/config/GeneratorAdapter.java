package com.zl.generator.extend.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.mybatis.generator.api.IntrospectedColumn;
import org.mybatis.generator.api.IntrospectedTable;
import org.mybatis.generator.api.PluginAdapter;
import org.mybatis.generator.api.dom.java.*;
import org.mybatis.generator.api.dom.xml.Attribute;
import org.mybatis.generator.api.dom.xml.Document;
import org.mybatis.generator.api.dom.xml.TextElement;
import org.mybatis.generator.api.dom.xml.XmlElement;
import org.mybatis.generator.codegen.mybatis3.MyBatis3FormattingUtilities;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @requiresDependencyResolution compile
 */
@Slf4j
public class GeneratorAdapter extends PluginAdapter{
    private final String deleteByPrimaryKey = "deleteByPrimaryKey";
    private final String insert = "insert";
    private final String insertSelective = "insertSelective";
    private final String selectByPrimaryKey = "selectByPrimaryKey";
    private final String updateByPrimaryKeySelective = "updateByPrimaryKeySelective";
    private final String updateByPrimaryKey = "updateByPrimaryKey";
    private final String baseColumnList = "Base_Column_List";
    private Set<String> mappers = new HashSet<>();
    // 要保存的值
    StringBuilder saveValueSelective = new StringBuilder("<trim prefix=\"values (\" suffix=\")\" suffixOverrides=\",\" > ");
    StringBuilder batchSaveValueSelective = new StringBuilder("<trim prefix=\"values (\" suffix=\")\" suffixOverrides=\",\" > ");
    // 要插入的字段(排除自增主键)
    StringBuilder saveColumnSelective = new StringBuilder("");
    StringBuilder batchSaveColumnSelective =new StringBuilder("");

    StringBuilder saveColumn = new StringBuilder("");
    StringBuilder saveValue = new StringBuilder("");


    StringBuilder updateSelectiveSQL = new StringBuilder("");
    StringBuilder updateSQL = new StringBuilder("");

    List<String> ignoreFields;

    public boolean validate(List<String> list) {
        return true;
    }

    @Override
    public void setProperties(Properties properties) {
        super.setProperties(properties);
        String mappers = this.properties.getProperty("mappers");
        for (String mapper : mappers.split(",")) {
            this.mappers.add(mapper);
        }
        String ignoreFieldsEntity = this.properties.getProperty("ignoreFieldsEntity");
        if (StringUtils.isBlank(ignoreFieldsEntity)) {
            return;
        }
        try {
            URLClassLoader urlClassLoader=new URLClassLoader(new URL[]{new URL("file:\\"+System.getProperty("user.dir")+"\\target\\classes\\")});
            Class<?> clazz = urlClassLoader.loadClass(ignoreFieldsEntity);
            ignoreFields = Arrays.stream(clazz.getDeclaredFields()).map(field -> field.getName()).collect(Collectors.toList());
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }

    }

    @Override
    public boolean clientGenerated(Interface interfaze, TopLevelClass topLevelClass, IntrospectedTable introspectedTable) {
        // 获取实体类
        FullyQualifiedJavaType entityType = new FullyQualifiedJavaType(introspectedTable.getBaseRecordType());
        // import接口
        for (String mapper : mappers) {
            interfaze.addImportedType(new FullyQualifiedJavaType(mapper));
            interfaze.addSuperInterface(new FullyQualifiedJavaType(mapper + "<" + entityType.getShortName() + ">"));
        }
        // import实体类
        interfaze.addImportedType(entityType);
        return true;
    }

    /**
     * 拼装SQL语句生成Mapper接口映射文件
     */
    public boolean sqlMapDocumentGenerated(Document document, IntrospectedTable introspectedTable) {
        XmlElement rootElement = document.getRootElement();
        // 数据库表名
        String tableName = introspectedTable.getFullyQualifiedTableNameAtRuntime();
        // 主键
        IntrospectedColumn pkColumn = introspectedTable.getPrimaryKeyColumns().get(0);

        // 公共字段
        StringBuilder columnSQL = new StringBuilder();

        // 拼装更新字段
        updateSelectiveSQL = new StringBuilder("update ").append(tableName).append(" \n\t<set>");
        updateSQL = new StringBuilder("update ").append(tableName).append("\n\t set ");
        // 数据库字段名
        String columnName = null;
        // java字段名
        String javaProperty = null;
        saveColumnSelective = saveColumnSelective.append("insert into ").append(tableName).append("\n\t<trim prefix=\"(\" suffix=\")\" suffixOverrides=\",\" >");
        batchSaveColumnSelective = batchSaveColumnSelective.append("\n\t<foreach collection=\"list\" item=\"item\" separator=\";\">\n\t")
                .append("\tinsert into ").append(tableName).append("\n\t\t<trim prefix=\"(\" suffix=\")\" suffixOverrides=\",\" >");

        saveValue.append(" values \n\t(");
        for (IntrospectedColumn introspectedColumn : introspectedTable.getAllColumns()) {
            columnName = MyBatis3FormattingUtilities.getEscapedColumnName(introspectedColumn);
            javaProperty = introspectedColumn.getJavaProperty();
            // 拼接字段
            columnSQL.append(columnName).append(",");
            // 拼接SQL
            if (!introspectedColumn.isAutoIncrement()) {
                saveColumnSelective.append("\n\t  <if test=\"").append(javaProperty).append(" != null\">").append("\n\t\t" + columnName).append(",\n\t  </if>");
                batchSaveColumnSelective.append("\n\t\t  <if test=\"item.").append(javaProperty).append(" != null\">").append("\n\t\t\t" + columnName).append(",\n\t\t  </if>");

                saveValueSelective.append("\n\t  <if test=\"").append(javaProperty + " != null").append("\"> ").append("\n\t\t #{").append(javaProperty)
                        .append(",jdbcType=").append(pkColumn.getJdbcTypeName()).append("},\n\t  </if>");
                batchSaveValueSelective.append("\n\t\t  <if test=\"item.").append(javaProperty + " != null").append("\"> ").append("\n\t\t\t #{item.").append(javaProperty)
                        .append(",jdbcType=").append(pkColumn.getJdbcTypeName()).append("},\n\t\t  </if>");

                saveValue.append("#{").append(javaProperty).append(",jdbcType=").append(pkColumn.getJdbcTypeName()).append("},\n\t");
                updateSelectiveSQL.append(" \n\t\t<if test=\"").append(javaProperty).append(" != null\">\n\t\t\t");
                updateSelectiveSQL.append(columnName).append(" = #{").append(javaProperty)
                        .append(",jdbcType=").append(pkColumn.getJdbcTypeName()).append("},\n\t\t</if>");
                updateSQL.append(columnName).append(" = #{").append(javaProperty)
                        .append(",jdbcType=").append(pkColumn.getJdbcTypeName()).append("}, \n\t");
            }
        }

        updateSelectiveSQL.append("\n\t</set>\n\t");

        saveColumnSelective.append("\n\t</trim>\n\t");
        batchSaveColumnSelective.append("\n\t\t</trim>\n\t");

        saveValueSelective.append("\n\t</trim>");
        batchSaveValueSelective.append("\n\t\t</trim>").append("</foreach>\n\t");

        String columns = columnSQL.substring(0, columnSQL.length() - 1);
        saveColumn = saveColumn.append("insert into ").append(tableName).append(" ( ").append(columns).append(" ) \n\t");
        saveValue.replace(saveValue.lastIndexOf(","), saveValue.lastIndexOf(",") + 1, "");
        saveValue.append(")");

        updateSQL.append("where ").append(pkColumn.getActualColumnName()).append(" = #{")
                .append(pkColumn.getJavaProperty()).append(",jdbcType=")
                .append(pkColumn.getJdbcTypeName()).append("}");
        updateSelectiveSQL.append("\twhere ").append(pkColumn.getActualColumnName()).append(" = #{")
                .append(pkColumn.getJavaProperty()).append(",jdbcType=").append(pkColumn.getJdbcTypeName()).append("}");

        //创建基础字段名
        rootElement.addElement(createSql(baseColumnList, columns));
        //创建selectByPrimaryKey方法
        rootElement.addElement(createSelect(selectByPrimaryKey, tableName, pkColumn));
        //创建insert方法
        rootElement.addElement(createSave(insert, pkColumn));
        //创建insertSelectvice方法
        rootElement.addElement(createSaveSelective(insertSelective, pkColumn));
        //创建updateByPrimaryKey方法
        rootElement.addElement(createUpdate(updateByPrimaryKey));
        //创建updateByPrimaryKeySelective
        rootElement.addElement(createUpdateByPrimaryKeySelective(updateByPrimaryKeySelective));
        return super.sqlMapDocumentGenerated(document, introspectedTable);
    }

    /**
     * 特殊字段无需自动生成
     */
    @Override
    public boolean modelFieldGenerated(Field field, TopLevelClass topLevelClass, IntrospectedColumn introspectedColumn, IntrospectedTable introspectedTable, ModelClassType modelClassType) {
        if (ignoreFields != null && !ignoreFields.isEmpty() && ignoreFields.contains(field.getName())) {
            return false;
        }
        return true;
    }
    private String upperCamelCase(String field){
        if(field.length()>1) {
           return String.valueOf(field.charAt(0)).toUpperCase().concat(field.substring(1));
        }else{
            return field.toUpperCase();
        }
    }
    /**
     *  忽略生成getter方法
     *
     * */
    @Override
    public boolean modelGetterMethodGenerated(Method method, TopLevelClass topLevelClass, IntrospectedColumn introspectedColumn, IntrospectedTable introspectedTable, ModelClassType modelClassType) {
        List<String> collect = ignoreFields.stream().filter(field -> method.getName().endsWith(upperCamelCase(field))).collect(Collectors.toList());
        if(collect!=null&&collect.size()>0){
            return false;
        }
        return true;
    }

    /**
     *  忽略生成setter方法
     *
     * */
    @Override
    public boolean modelSetterMethodGenerated(Method method, TopLevelClass topLevelClass, IntrospectedColumn introspectedColumn, IntrospectedTable introspectedTable, ModelClassType modelClassType) {
        List<String> collect = ignoreFields.stream().filter(field -> method.getName().endsWith(upperCamelCase(field))).collect(Collectors.toList());
        if(collect!=null&&collect.size()>0){
            return false;
        }
        return true;
    }
    @Override
    public boolean clientSelectAllMethodGenerated(Method method, Interface interfaze, IntrospectedTable introspectedTable) {
        return false;
    }

    @Override
    public boolean clientSelectAllMethodGenerated(Method method, TopLevelClass topLevelClass, IntrospectedTable introspectedTable) {
        return false;
    }

    @Override
    public boolean sqlMapSelectAllElementGenerated(XmlElement element, IntrospectedTable introspectedTable) {
        return false;
    }

    /**
     * 公共SQL
     *
     * @param id
     * @param sqlStr
     * @return
     */
    private XmlElement createSql(String id, String sqlStr) {
        XmlElement sql = new XmlElement("sql");
        sql.addAttribute(new Attribute("id", id));
        sql.addElement(new TextElement(sqlStr));
        return sql;
    }

    /**
     * 查询
     *
     * @param id
     * @param tableName
     * @param pkColumn
     * @return
     */
    private XmlElement createSelect(String id, String tableName, IntrospectedColumn pkColumn) {
        XmlElement select = new XmlElement("select");
        select.addAttribute(new Attribute("id", id));
        select.addAttribute(new Attribute("resultMap", "BaseResultMap"));

        StringBuilder selectStr = new StringBuilder("select <include refid=\"" + baseColumnList + "\" /> from ").append(tableName);
        if (null != pkColumn) {
            selectStr.append(" where ").append(pkColumn.getActualColumnName()).append(" = #{").append(pkColumn.getJavaProperty()).append(",jdbcType=").append(pkColumn.getJdbcTypeName()).append("}");
        } else {
            selectStr.append(" <include refid=\"sql_where\" />");
        }
        if ("selectPage".equals(id)) {
            selectStr.append(" limit #{page.startRow}, #{page.pageSize}");
        }
        select.addElement(new TextElement(selectStr.toString()));
        return select;
    }

    /**
     * 保存
     *
     * @param id
     * @param pkColumn
     * @return
     */
    private XmlElement createSave(String id, IntrospectedColumn pkColumn) {
        XmlElement save = new XmlElement("insert");
        save.addAttribute(new Attribute("id", id));
        if (null != pkColumn) {
            save.addAttribute(new Attribute("keyProperty", pkColumn.getJavaProperty()));
            save.addAttribute(new Attribute("useGeneratedKeys", "true"));
            save.addElement(new TextElement(saveColumn.toString() + saveValue.toString()));
        } else {
            StringBuilder saveStr = new StringBuilder("")
                    .append(saveColumn.toString() + saveValue.toString());
            save.addElement(new TextElement(saveStr.toString()));
        }
        return save;
    }

    /**
     * 保存
     *
     * @param id
     * @param pkColumn
     * @return
     */
    private XmlElement createSaveSelective(String id, IntrospectedColumn pkColumn) {
        XmlElement save = new XmlElement("insert");
        save.addAttribute(new Attribute("id", id));
        if (null != pkColumn) {
            save.addAttribute(new Attribute("keyProperty", pkColumn.getJavaProperty()));
            save.addAttribute(new Attribute("useGeneratedKeys", "true"));
            save.addElement(new TextElement(saveColumnSelective.toString() + saveValueSelective.toString()));
        } else {
            StringBuilder saveStr = new StringBuilder("")
                    .append(saveColumnSelective.toString() + saveValueSelective.toString());

            save.addElement(new TextElement(saveStr.toString()));
        }
        return save;
    }
    /**
     * 批量保存
     *
     * @param id
     * @param pkColumn
     * @return
     */
    private XmlElement createBatchSaveSelective(String id, IntrospectedColumn pkColumn) {
        XmlElement save = new XmlElement("insert");
        save.addAttribute(new Attribute("id", id));
        if (null != pkColumn) {
            save.addElement(new TextElement(batchSaveColumnSelective.toString() + batchSaveValueSelective.toString()));
        } else {
            StringBuilder saveStr = new StringBuilder("")
                    .append(batchSaveColumnSelective.toString() + batchSaveValueSelective.toString());

            save.addElement(new TextElement(saveStr.toString()));
        }
        return save;
    }

    /**
     * 更新
     *
     * @param id
     * @return
     */
    private XmlElement createUpdate(String id) {
        XmlElement update = new XmlElement("update");
        update.addAttribute(new Attribute("id", id));
        if ("update".equals(id)) {
            update.addElement(new TextElement(saveColumn.append("\t) values").toString().replaceFirst(",", "")));
        } else {
            update.addElement(new TextElement(updateSQL.toString()));
        }
        return update;
    }

    /**
     * 更新
     *
     * @param id
     * @return
     */
    private XmlElement createUpdateByPrimaryKeySelective(String id) {
        XmlElement update = new XmlElement("update");
        update.addAttribute(new Attribute("id", id));
        if ("update".equals(id)) {
            update.addElement(new TextElement(saveColumn.append("\t) values").toString().replaceFirst(",", "")));
        } else {
            update.addElement(new TextElement(updateSelectiveSQL.toString()));
        }
        return update;
    }

    /**
     * 删除(暂时不用)
     *
     * @param tableName
     * @param pkColumn
     * @param method
     * @param type
     * @return
     */
    private XmlElement createDels(String tableName, IntrospectedColumn pkColumn, String method, String type) {
        XmlElement delete = new XmlElement("delete");
        delete.addAttribute(new Attribute("id", method));
        StringBuilder deleteStr = new StringBuilder("delete from ").append(tableName).append(" where ").append(pkColumn.getActualColumnName())
                .append(" in\n\t")
                .append("<foreach collection=\"").append(type)
                .append("\" index=\"index\" item=\"item\" open=\"(\" separator=\",\" close=\")\">#{item}</foreach>");
        delete.addElement(new TextElement(deleteStr.toString()));
        return delete;
    }
}
