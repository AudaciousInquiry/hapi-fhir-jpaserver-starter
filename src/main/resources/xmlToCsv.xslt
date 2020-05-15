<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:fn="fn"
  xmlns:xs="http://www.w3.org/2001/XMLSchema" version="2.0" exclude-result-prefixes="xs fn">
  <xsl:output indent="no" encoding="UTF-8" method="text"/>
  <xsl:strip-space elements="*"/>
  <xsl:template match="/">
    <xsl:apply-templates select="*|@*|text()" mode="headers"/>
    <xsl:text>&#xA;</xsl:text>
    <xsl:apply-templates select="*|@*|text()" mode="columns"/>
  </xsl:template>
  <xsl:template match="text()|@*" mode="headers">
    <xsl:for-each select="(ancestor-or-self::node())[true()]">
      <xsl:if test="not(. instance of text())">
        <xsl:if test="position()!=1">/</xsl:if>
        <xsl:if test=". instance of attribute()">
          <xsl:text>@</xsl:text>
        </xsl:if>
        <xsl:value-of select="local-name(.)"/>
        <xsl:if test=". instance of element() and .. instance of element()">
          <xsl:text>[</xsl:text>
          <xsl:value-of select="count(preceding-sibling::*[local-name()=local-name(current())])+1"/>
          <xsl:text>]</xsl:text>
        </xsl:if>
      </xsl:if>
    </xsl:for-each>
    <xsl:text>,</xsl:text>
  </xsl:template>
  <xsl:template match="*" mode="headers">
    <xsl:apply-templates select="*|@*|text()" mode="headers"/>
  </xsl:template>
  <xsl:template match="text()|@*" mode="columns">
    <xsl:text>"</xsl:text>
    <xsl:value-of select="replace(.,'&quot;','&quot;&quot;')"/>
    <xsl:text>",</xsl:text>
  </xsl:template>
  <xsl:template match="*" mode="columns">
    <xsl:apply-templates select="*|@*|text()" mode="columns"/>
  </xsl:template>
</xsl:stylesheet>
