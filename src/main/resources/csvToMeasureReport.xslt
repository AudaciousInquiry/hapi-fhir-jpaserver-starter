<?xml version="1.0" encoding="UTF-8"?>

<!--
    This XSLT will consume a CSV input file and convert it to a MeasureReport
    against the selected Measure.
    -->
<xsl:stylesheet xmlns="http://hl7.org/fhir" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:uuid="http://www.uuid.org"
    xmlns:s="https://github.com/FHIR/sushi" xmlns:f="http://hl7.org/fhir"
    xmlns:saxon="http://saxon.sf.net/" extension-element-prefixes="saxon"
    exclude-result-prefixes="xs f s uuid" version="2.0">
    <!-- Import CSV to XML utilities -->
    <xsl:import href="CSVtoXML.xslt"/>

    <!-- Import UUID Generation utilities -->
    <xsl:import href="uuid.xslt"/>
    <xsl:output indent="yes" method="xml" omit-xml-declaration="yes"/>
    <xsl:strip-space elements="*"/>

    <!-- Limit the number of rows to convert, set to 0 to convert everything -->
    <xsl:param name="count" select="2000"/>

    <xsl:param name="periodStart"/>
    <xsl:param name="periodEnd"/>
    <xsl:param name="reporter"/>
    <xsl:param name="reporter-display"/>
    <xsl:param name="reporter-identifier"/>
    <xsl:param name="subject"/>
    <xsl:param name="subject-display"/>
    <xsl:param name="subject-identifier"/>
    <xsl:param name="Lat"/>
    <xsl:param name="Long"/>
    <xsl:param name="date" select="current-dateTime()"/>
    <xsl:param name="measure" select="document('file:///C:\temp\Measure-CDCPatientImpactAndHospitalCapacity.xml')"/>
    <!-- set to the name of the mapping file


        The mapping file should have the names of the CSV file columns in the first column
        and the codes that they map to in the column row.  Headers should for the first
        row should be column,item  e.g.:

        column,item
        deathIncrease,numC19died
        hospitalizedCurrently,numC19HospPats
        hospitalizedIncrease,numC19Pats
        inIcuCurrently,numC19VentPats
        numAcuteBeds,numBeds
        numICUBeds,numICUBeds
        numTotBeds,numTotBeds
        numVent,numVent
        numVentUse,numVentUse
        -->
    <xsl:param name="mapping" select="'Mapping.csv'"/>

    <!-- Specifies the source of the input data.  The first row
        must be column names for the data.  The second and subsequent
        rows should be the data to convert to measurements -->
    <xsl:param name="csvInputData" select="unparsed-text('file:C:/temp/CRISP.csv')"/>

    <!-- Set the BASE URL for saner IG artifacts (we changed it once) -->
    <xsl:variable name="base" select="'http://hl7.org/fhir/us/saner/'"/>

    <!-- Load up the mapping file and convert to XML for XSLT processing -->
    <xsl:param name="map">
        <map xmlns="">
            <Beds value="Beds"/>
            <numTotBeds value="numTotBeds"/>
            <numBeds value="Physical Adult Acute Care Beds"/>
            <numBedsOcc value="Occupied Adult Acute Care Beds"/>
            <numICUBeds value="Physical Adult ICU Beds"/>
            <numICUBedsOcc value="Occupied Adult ICU Beds"/>
            <Ventilators value="Ventilators"/>
            <numVent value="Ventilators Available"/>
            <numVentUse value="Ventilators in Use"/>
            <Encounters value="Encounters"/>
            <numC19HospPats value="COVID-19 Patients in Acute Care Beds"/>
            <numC19MechVentPats value="numC19MechVentPats"/>
            <numC19HOPats value="numC19HOPats"/>
            <numC19OverflowPats value="numC19OverflowPats"/>
            <numC19OFMechVentPats value="numC19OFMechVentPats"/>
            <numC19Died value="numC19Died"/>
            <period.start value="Modified Date"/>
            <period.end value="Modified Date"/>
            <subject.display value="Facility Name"/>
        </map>
    </xsl:param>

    <xsl:variable name="def" select="$measure"/>
    <!-- Load up the Measure definition resource -->
    <!-- xsl:variable name="def" select='document($measureResource)'/ -->


    <!-- Process the input -->
    <xsl:template match="/">
        <!-- Load the CSV file to process -->
        <xsl:variable name="res">
            <xsl:call-template name="getSheetAsXML">
                <xsl:with-param name="names" select="('results', 'result')"/>
                <xsl:with-param name="attribute">value</xsl:with-param>
                <xsl:with-param name="useFieldNames" select="false()"/>
                <xsl:with-param name="pathToCSV" select="$csvInputData"/>
            </xsl:call-template>
        </xsl:variable>
        <!--xsl:message>Res: <xsl:copy-of select="$res/results"/></xsl:message-->
        <xsl:apply-templates select="$res/results/result[position() &lt; $count]"/>
    </xsl:template>

    <xsl:template match="result">
        <xsl:call-template name="result"/>
    </xsl:template>

    <xsl:function name="s:name">
        <xsl:param name="f"/>
        <xsl:param name="v"/>
        <xsl:variable name="lastPart"
            select="translate(tokenize(string-join($f), '\.')[last()], '[]0123456789', '')"/>
        <xsl:element name="{$lastPart}">
            <xsl:attribute name="value" select="string-join($v)"/>
        </xsl:element>
    </xsl:function>

    <xsl:function name="s:string">
        <xsl:param name="f"/>
        <xsl:param name="v"/>
        <xsl:if test="$v">
            <xsl:variable name="lastPart"
                select="translate(tokenize(string-join($f), '\.')[last()], '[]0123456789', '')"/>
            <xsl:element name="{$lastPart}">
                <xsl:attribute name="value" select="string-join($v)"/>
            </xsl:element>
        </xsl:if>
    </xsl:function>
    <xsl:function name="s:code">
        <xsl:param name="f"/>
        <xsl:param name="c"/>
        <xsl:param name="s"/>
        <xsl:variable name="lastPart"
            select="translate(tokenize(string-join($f), '\.')[last()], '[]0123456789', '')"/>
        <xsl:choose>
            <xsl:when test="not($s)">
                <xsl:element name="{$lastPart}">
                    <xsl:attribute name="value" select="$c"/>
                </xsl:element>
            </xsl:when>
            <xsl:otherwise>
                <xsl:element name="{$lastPart}">
                    <xsl:attribute name="code" select="$c"/>
                    <xsl:attribute name="system" select="$s"/>
                </xsl:element>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:function>
    <xsl:function name="s:def">
        <xsl:param name="n"/>
        <xsl:param name="v"/>
        <xsl:variable name="lastPart"
            select="translate(tokenize(string-join($n), '\.')[last()], '[]0123456789', '')"/>
        <xsl:element name="def-{$lastPart}">
            <xsl:attribute name="value" select="string-join($v, '')"/>
        </xsl:element>
    </xsl:function>
    <xsl:function name="s:instance">
        <xsl:param name="t"/>
        <xsl:param name="d"/>
        <xsl:param name="body"/>
        <xsl:element name="{$t}">
            <xsl:copy-of select="$body"/>
        </xsl:element>
    </xsl:function>

    <xsl:function name="s:wrap">
        <xsl:param name="elemName"/>
        <xsl:param name="body"/>
        <xsl:element name="{$elemName}">
            <xsl:copy-of select="$body"/>
        </xsl:element>
    </xsl:function>

    <xsl:function name="s:identifier">
        <xsl:param name="f"/>
        <xsl:param name="value"/>
        <xsl:variable name="lastPart"
            select="translate(tokenize(string-join($f), '\.')[last()], '[]0123456789', '')"/>
        <xsl:if test="$value">
            <xsl:copy-of
                select="
                    s:wrap($lastPart,
                    (s:string(('system'), substring-before($value, '#')),
                    s:string(('value'), substring-after($value, '#'))
                    )
                    )"
            />
        </xsl:if>
    </xsl:function>

    <xsl:template name="result">

        <!-- Take the data row, and compute some values into it -->
        <!-- NOTE: We should be able to automate for
            (numerator - sum(numerator-exclusion))/(denominator - sum(denominator-exclusion))
            -->
        <xsl:variable name="data">
            <result xmlns="">
                <xsl:copy-of select="*"/>
                <xsl:variable name="num1" select="positive/@value"/>
                <xsl:variable name="denom1" select="totalTestResults/@value"/>
                <xsl:if test="$num1 and $denom1 &gt; 0">
                    <positivePercent xmlns="" value="{round( 10000 * $num1 div $denom1) div 100}"
                        unit="%"/>
                </xsl:if>
                <xsl:variable name="num2" select="positiveIncrease/@value"/>
                <xsl:variable name="denom2" select="totalTestResultsIncrease/@value"/>
                <xsl:if test="$num2 and $denom2 &gt; 0">
                    <positiveIncreasePercent xmlns=""
                        value="{round( 10000 * $num2 div $denom2) div 100}" unit="%"/>
                </xsl:if>
            </result>
        </xsl:variable>


        <!-- Generate Instance -->
        <xsl:call-template name="createMeasureReportHeader">
            <xsl:with-param name="date" select="string($date)"/>
            <xsl:with-param name="def" select="$def"/>
            <xsl:with-param name="body">
                <xsl:apply-templates select="$def/f:Measure/f:group" mode="copyMeasureToReport">
                    <xsl:with-param name="values" select="$data"/>
                </xsl:apply-templates>
            </xsl:with-param>
        </xsl:call-template>

    </xsl:template>

    <xsl:function name="s:coalesce">
        <xsl:param name="a"/>
        <xsl:param name="b"/>
        <xsl:value-of select="if ($a) then $a else $b"/>
    </xsl:function>
    <xsl:function name="s:makeDate">
        <xsl:param name="year"/>
        <xsl:param name="month"/>
        <xsl:param name="day"/>
        <xsl:param name="hour"/>
        <xsl:param name="min"/>
        <xsl:param name="sec"/>
        <xsl:param name="ampm"/>
        <xsl:value-of select="format-number(number($year),'0000')"/>
        <xsl:text>-</xsl:text>
        <xsl:variable name="mon" select="substring(lower-case($month),1,3)"/>
        <xsl:choose>
            <xsl:when test="$mon='jan'">01</xsl:when>
            <xsl:when test="$mon='feb'">02</xsl:when>
            <xsl:when test="$mon='mar'">03</xsl:when>
            <xsl:when test="$mon='apr'">04</xsl:when>
            <xsl:when test="$mon='may'">05</xsl:when>
            <xsl:when test="$mon='jun'">06</xsl:when>
            <xsl:when test="$mon='jul'">07</xsl:when>
            <xsl:when test="$mon='aug'">08</xsl:when>
            <xsl:when test="$mon='sep'">09</xsl:when>
            <xsl:when test="$mon='oct'">10</xsl:when>
            <xsl:when test="$mon='nov'">11</xsl:when>
            <xsl:when test="$mon='dec'">12</xsl:when>
            <xsl:otherwise>
                <xsl:value-of select="format-number(number($month),'00')"/>
            </xsl:otherwise>
        </xsl:choose>
        <xsl:text>-</xsl:text>
        <xsl:value-of select="format-number(number($day),'00')"/>
        <xsl:if test='$hour'>
            <xsl:text>T</xsl:text>
            <xsl:choose>
                <xsl:when test="lower-case($ampm)='pm'">
                    <xsl:value-of select="format-number(number($hour)+12,'00')"/>
                </xsl:when>
                <xsl:otherwise>
                    <xsl:value-of select="format-number(number($hour),'00')"/>
                </xsl:otherwise>
            </xsl:choose>
            <xsl:text>:</xsl:text>
            <xsl:value-of select="format-number(number($min),'00')"/>
            <xsl:if test='$sec'>
                <xsl:text>:</xsl:text>
                <xsl:value-of select="format-number(number($sec),'00.###')"/>
            </xsl:if>
        </xsl:if>
    </xsl:function>
    
    <xsl:function name="s:fixDate">
        <xsl:param name="a"/>
        <xsl:choose>
            <xsl:when test="matches($a,'\d+/\d+/\d+.*')">
                <!-- Assume US mm/dd/yyyy format -->
                <xsl:variable name="parts" select="tokenize($a, '[/\s:T]+')"/>
                <xsl:value-of select='s:makeDate($parts[3],$parts[1],$parts[2],$parts[4], $parts[5], $parts[6], $parts[7])'/>
            </xsl:when>
            <xsl:when test="matches($a,'\d+-\d+-\d+.*')">
                <!-- Assume yyyy-mm-dd format -->
                <xsl:variable name="parts" select="tokenize($a, '[\-\s:T]+')"/>
                <xsl:value-of select='s:makeDate($parts[1],$parts[2],$parts[3],$parts[4], $parts[5], $parts[6], $parts[7])'/>
            </xsl:when>
            <xsl:when test="matches($a,'\d+-[A-Z]{3}-\d+.*$')">
                <xsl:variable name="parts" select="tokenize($a, '[\-\s:]+')"/>
                <xsl:value-of select='s:makeDate($parts[3],$parts[2],$parts[1],$parts[4], $parts[5], $parts[6], $parts[7])'/>
            </xsl:when>
        </xsl:choose>
    </xsl:function>

    <xsl:template name="createMeasureReportHeader">
        <xsl:param name="date"/>
        <xsl:param name="def"/>
        <xsl:param name="body"/>

        <xsl:variable name="measure" select="$def/f:Measure/f:id/@value"/>

        <xsl:variable name="measureBody">
            <xsl:copy-of
                select="
                    s:wrap('identifier',
                    (s:string(('identifier.system'), 'urn:ietf:rfc:3986'),
                    s:string(('identifier.value'), concat('urn:uuid:', lower-case(uuid:get-uuid($body))))
                    ))"/>

            <xsl:copy-of select="s:code(('status'), 'complete', null)"/>
            <xsl:copy-of select="s:code(('type'), 'summary', null)"/>
            <xsl:copy-of select="s:string(('measure'), ($def/f:Measure/f:url/@value))"/>

            <xsl:variable name="subj"
                select="s:coalesce(./elem[@name=$map/map/subject/@value]/@value, $subject)"/>
            <xsl:variable name="subj-display"
                select="s:coalesce(./elem[@name=$map/map/subject.display/@value]/@value, $subject-display)"/>
            <xsl:variable name="subj-identifier"
                select="s:coalesce(./elem[@name=$map/map/subject.identifier/@value]/@value, $subject-identifier)"/>
            <xsl:variable name="rep"
                select="s:coalesce(./elem[@name=$map/map/reporter/@value]/@value, $reporter)"/>
            <xsl:variable name="rep-display"
                select="s:coalesce(./elem[@name=$map/map/reporter.display/@value]/@value, $reporter-display)"/>
            <xsl:variable name="rep-identifier"
                select="s:coalesce(./elem[@name=$map/map/reporter.identifier/@value]/@value, $reporter-identifier)"/>
            <xsl:variable name="per-start"
                select="s:fixDate(s:coalesce(./elem[@name=$map/map/period.start/@value]/@value, $periodStart))"/>
            <xsl:variable name="per-end"
                select="s:fixDate(s:coalesce(./elem[@name=$map/map/period.end/@value]/@value, $periodEnd))"/>
            <xsl:choose>
                <xsl:when test='not($subj|$subj-display|$subj-identifier)'>
                    <subject>
                        <extension url="http://hl7.org/fhir/StructureDefinition/data-absent-reason">
                            <valueCode value="unknown" />
                        </extension>
                    </subject>
                </xsl:when>
                <xsl:otherwise>
                    <xsl:copy-of
                        select="s:wrap('subject',
                            (s:string(('subject.reference'), ($subj)),
                            s:string(('subject.display'), ($subj-display)),
                            s:identifier(('subject.identifier'), ($subj-identifier))
                            )
                            )"/>
                </xsl:otherwise>
            </xsl:choose>
            <xsl:copy-of select="s:string(('date'), (string($date)))"/>
            <xsl:choose>
                <xsl:when test="not($rep|$rep-display|$rep-identifier)">
                    <reporter>
                        <extension url="http://hl7.org/fhir/StructureDefinition/data-absent-reason">
                            <valueCode value="unknown" />
                        </extension>
                    </reporter>
                </xsl:when>
                <xsl:otherwise>
                    <xsl:copy-of
                        select="
                        s:wrap('reporter',
                        (s:string(('reporter.reference'), ($rep)),
                        s:string(('reporter.display'), ($rep-display)),
                        s:identifier(('reporter.identifier'), ($rep-identifier))
                        )
                        )"/>
                </xsl:otherwise>
            </xsl:choose>

            <xsl:choose>
                <xsl:when test="not($per-start|$per-end)">
                    <period>
                        <extension url="http://hl7.org/fhir/StructureDefinition/data-absent-reason">
                            <valueCode value="unknown" />
                        </extension>
                    </period>
                </xsl:when>
                <xsl:otherwise>
                    <xsl:copy-of
                        select="
                        s:wrap('period',
                        (
                        s:string(('period.start'), $per-start),
                        s:string(('period.end'), $per-end))
                        )"/>
                </xsl:otherwise>
            </xsl:choose>
            <xsl:copy-of select="$body"/>
        </xsl:variable>
        <xsl:copy-of
            select="
                s:instance(
                'MeasureReport',
                ('MeasureReport of ', $measure, ' for ', $subject-display, ' on ', (substring($date, 5, 2), '/', substring($date, 7, 2), '/', substring($date, 1, 4))),
                $measureBody)"
        />
    </xsl:template>

    <xsl:function name="s:getName">
        <xsl:param name="s"/>
        <xsl:if test="lower-case(substring(local-name($s), 1, 1)) = substring(local-name($s), 1, 1)">
            <xsl:variable name="p">
                <xsl:value-of select="s:getName($s/..)"/>
            </xsl:variable>
            <xsl:if test="string-length($p) != 0">
                <xsl:value-of select="$p"/>
                <xsl:text>.</xsl:text>
            </xsl:if>
            <xsl:value-of select="local-name($s)"/>
            <xsl:if test="count($s/preceding-sibling::f:*[local-name() = local-name($s)]) != 0">
                <xsl:text>[</xsl:text>
                <xsl:value-of
                    select="count($s/preceding-sibling::f:*[local-name() = local-name($s)])"/>
                <xsl:text>]</xsl:text>
            </xsl:if>
        </xsl:if>
    </xsl:function>

    <xsl:template match="@value" mode="copyMeasureToReport">
        <xsl:variable name="name" select="s:getName(..)"/>
        <xsl:copy-of
            select="
                if (contains(local-name(../..), 'coding')) then
                    (../..)
                else
                    (..)"
        />
    </xsl:template>

    <xsl:template match="@url" mode="copyMeasureToReport">
        <xsl:copy-of select="."/>
    </xsl:template>

    <xsl:template
        match='f:extension[@url = "http://hl7.org/fhir/us/saner/StructureDefinition/MeasureGroupAttributes"]'
        mode="copyMeasureToReport">
        <!-- Skip this extension, it's not needed in the report, just the measure -->
    </xsl:template>

    <!-- f:code[not(../f:coding)] | f:coding  | f:text | -->
    <xsl:template
        match="f:group | f:population | (f:group | f:population)/f:code | f:stratifier | f:value | f:extension | f:valueCodeableConcept | f:valueString"
        mode="copyMeasureToReport">
        <xsl:param name="values"/>
        <xsl:variable name="content">
            <content>
                <xsl:apply-templates select="@*" mode="copyMeasureToReport"/>
                <xsl:choose>
                    <xsl:when test="self::f:code">
                        <xsl:apply-templates select="f:coding/f:code/@value"
                            mode="copyMeasureToReport"/>
                    </xsl:when>
                    <xsl:otherwise>
                        <xsl:apply-templates select="*" mode="copyMeasureToReport">
                            <xsl:with-param name="values" select="$values"/>
                        </xsl:apply-templates>
                    </xsl:otherwise>
                </xsl:choose>

                <!-- If this is a group|population, we may need to output a measure score|count -->
                <xsl:if test="self::f:group | self::f:population">
                    <xsl:variable name="score"
                        select="f:code/f:coding[starts-with(f:system/@value, 'http://hl7.org/fhir/us/saner/CodeSystem/Measure')]/f:code/@value"/>
                    <xsl:variable name="mappedScore"
                        select="$map/map/*[local-name() = $score]/@value"/>
                    <!--xsl:message>From: <xsl:value-of select="$score"/> -> To: <xsl:value-of
                            select="$mappedScore"/></xsl:message-->
                    <xsl:variable name="v"
                        select="$values/result/elem[@name = string($mappedScore)]"/>
                    <!-- If this is a group, and the group reports a score and the score has a value -->
                    <xsl:variable name="n" select="s:getName(.)"/>
                    <xsl:choose>
                        <xsl:when test="self::f:group">
                            <xsl:choose>
                                <xsl:when test="not(f:measureScore)">
                                    <!-- Don't try to report a measureScore that isn't defined -->
                                </xsl:when>
                                <xsl:when test="$v">
                                    <xsl:copy-of
                                        select="s:name(($n, '.measureScore.value'), $v/@value)"/>
                                    <xsl:if test="$v/@unit">
                                        <xsl:copy-of
                                            select="s:string(($n, '.measureScore.unit'), ($v/@unit))"/>
                                        <xsl:copy-of
                                            select="s:code(($n, '.measureScore.code'), $v/@unit, 'http://unitsofmeasure.org')"
                                        />
                                    </xsl:if>
                                </xsl:when>
                                <xsl:otherwise>
                                    <measureScore>
                                        <value>
                                            <extension
                                                url="http://hl7.org/fhir/StructureDefinition/data-absent-reason">
                                                <valueCode
                                                  value="{if (not($mappedScore)) then 'unsupported' else 'unknown'}"
                                                />
                                            </extension>
                                        </value>
                                    </measureScore>
                                </xsl:otherwise>
                            </xsl:choose>
                        </xsl:when>
                        <xsl:when test="self::f:population">
                            <xsl:choose>
                                <xsl:when test="$v">
                                    <xsl:copy-of select="s:name(($n, '.count'), $v/@value)"/>
                                </xsl:when>
                                <xsl:otherwise>
                                    <count>
                                        <extension
                                            url="http://hl7.org/fhir/StructureDefinition/data-absent-reason">
                                            <valueCode
                                                value="{if (not($mappedScore)) then 'unsupported' else 'unknown'}"
                                            />
                                        </extension>
                                    </count>
                                </xsl:otherwise>
                            </xsl:choose>
                        </xsl:when>
                    </xsl:choose>
                </xsl:if>
            </content>
        </xsl:variable>
        <xsl:copy>
            <xsl:copy-of select="$content/f:content/@*"/>
            <xsl:copy-of select="$content/f:content/*"/>
        </xsl:copy>
    </xsl:template>
</xsl:stylesheet>
