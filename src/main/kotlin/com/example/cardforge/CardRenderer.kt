package com.example.cardforge

import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Cursor
import javafx.scene.control.Label
import javafx.scene.control.OverrunStyle
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.input.MouseButton
import javafx.scene.input.MouseEvent
import javafx.scene.input.ScrollEvent
import javafx.scene.layout.Pane
import javafx.scene.layout.StackPane
import javafx.scene.paint.Color
import javafx.scene.shape.Line
import javafx.scene.shape.Polygon
import javafx.scene.shape.Rectangle
import javafx.scene.text.Font
import javafx.scene.text.FontPosture
import javafx.scene.text.FontWeight
import kotlin.math.max
import kotlin.math.min

object CardRenderer {
    const val DEFAULT_W = 630.0
    const val DEFAULT_H = 880.0
    const val DEFAULT_ART_W = 546.0
    const val DEFAULT_ART_H = 386.0

    data class Rendered(val root: StackPane, val imageView: ImageView, val viewport: StackPane, val width: Double, val height: Double)

    fun build(
        data: CardData,
        image: Image?,
        template: CardTemplate,
        templateImage: Image?,
        backgroundOverlay: Image?,
        collectionPresentation: CollectionPresentation = CollectionPresentation(),
        onImageDragged: (dx: Double, dy: Double) -> Unit,
        onImageZoomed: (delta: Double) -> Unit = {},
        onImageReset: () -> Unit = {},
        showCropGuides: Boolean = false
    ): Rendered {
        val palette = CardVisualSystem.palette(data)
        val visual = template.visualStyleFor(data.rarity)
        val root = StackPane().apply {
            styleClass.add("card-rendered")
            style = "-fx-background-color: transparent;"
            prefWidth = template.width; prefHeight = template.height
            minWidth = template.width; minHeight = template.height
            maxWidth = template.width; maxHeight = template.height
        }
        val outer = StackPane().apply {
            isManaged = false
            prefWidth = template.width; prefHeight = template.height
            minWidth = template.width; minHeight = template.height
            maxWidth = template.width; maxHeight = template.height
            resize(template.width, template.height); relocate(0.0, 0.0)
            val borderColor = if (visual.rarityFrames) palette.outerFrame else data.accentColor
            style = "-fx-background-color:linear-gradient(to bottom right,${CardVisualSystem.lighten(data.backgroundColor, 0.06)},${data.backgroundColor} 48%,${CardVisualSystem.darken(data.backgroundColor, 0.18)});-fx-background-radius:${data.cornerRadius}px;" +
                "-fx-border-color:$borderColor;-fx-border-width:${data.borderWidth}px;-fx-border-radius:${data.cornerRadius}px;"
            clip = Rectangle(template.width, template.height).apply { arcWidth = data.cornerRadius * 2; arcHeight = data.cornerRadius * 2 }
        }

        outer.children.add(materialFrameLayer(template, data, palette))
        templateImage?.let { outer.children.add(ImageView(it).apply { isPreserveRatio=false; fitWidth=template.width; fitHeight=template.height; isMouseTransparent=true }) }

        val bleedImageView = if (data.imageBleedOverFrame && image != null) ImageView(image).apply {
            isSmooth=true; isManaged=false; isMouseTransparent=true
            opacity = CollectionVisualSettings.bleedOpacity(data, collectionPresentation)
            updateBleedImageView(this, image, data, template, collectionPresentation)
        } else null
        if (bleedImageView != null) {
            val inset = CollectionVisualSettings.bleedInset(data)
            val bleedLayer = Pane().apply {
                isManaged=false; resize(template.width, template.height); relocate(0.0, 0.0)
                prefWidth=template.width; prefHeight=template.height
                isMouseTransparent=true
                clip = Rectangle(
                    inset, inset,
                    (template.width - inset * 2).coerceAtLeast(0.0),
                    (template.height - inset * 2).coerceAtLeast(0.0)
                ).apply {
                    val innerRadius = (data.cornerRadius - inset).coerceAtLeast(0.0)
                    arcWidth=innerRadius*2; arcHeight=innerRadius*2
                }
            }
            bleedLayer.children.add(bleedImageView)
            outer.children.add(bleedLayer)
        }

        if (backgroundOverlay != null && data.backgroundOverlayPlacement == OverlayPlacement.FRAMES_ONLY) outer.children.add(overlayLayer(backgroundOverlay, template, data))

        val content = Pane().apply {
            isManaged=false; prefWidth=template.width; prefHeight=template.height
            minWidth=template.width; minHeight=template.height; maxWidth=template.width; maxHeight=template.height
            resize(template.width, template.height); relocate(0.0,0.0)
        }
        val fgOpacity = CollectionVisualSettings.foregroundOpacity(collectionPresentation)
        fun label(text:String,size:Double,bold:Boolean,color:String)=Label(text).apply {
            val weight=if(bold) FontWeight.BOLD else FontWeight.NORMAL
            font=Font.font("Georgia",weight,size); textFill=Color.web(color); style="-fx-font-family:'Georgia';-fx-text-fill:$color;"; isWrapText=true
        }

        val titleBox=railBox(template.titleBox,data.backgroundColor,palette.outerFrame,visual,fgOpacity); position(titleBox,template.titleBox)
        val titleSize=CardVisualSystem.fitFontSize(data.title,data.titleFontSize,18.0,template.titleText.width,template.titleText.height,true)
        val titleLabel=label(data.title,titleSize,true,data.darkTextColor); place(titleLabel,template.titleText)
        val costLabel=label("◇ ${data.cost}",19.0,true,palette.outerFrame); place(costLabel,template.costText)
        content.children.addAll(titleBox,titleLabel,costLabel)

        val artFrame=Pane().apply { isManaged=false; prefWidth=template.art.width;prefHeight=template.art.height;minWidth=template.art.width;minHeight=template.art.height;maxWidth=template.art.width;maxHeight=template.art.height;resize(template.art.width,template.art.height) }
        val frameRect=Rectangle(template.art.width,template.art.height).apply { arcWidth=template.art.radius;arcHeight=template.art.radius;fill=Color.web(data.imagePadColor);stroke=Color.web(data.accentColor);strokeWidth=4.0 }
        val viewport=StackPane().apply {
            isManaged=false;prefWidth=template.art.width;prefHeight=template.art.height;minWidth=template.art.width;minHeight=template.art.height;maxWidth=template.art.width;maxHeight=template.art.height;resize(template.art.width,template.art.height);relocate(0.0,0.0)
            clip=Rectangle(template.art.width,template.art.height).apply{arcWidth=template.art.radius;arcHeight=template.art.radius}
        }
        val imageView=ImageView(image).apply{isSmooth=true;isManaged=false}
        if(bleedImageView!=null){ imageView.properties["cardforge.bleedImageView"]=bleedImageView; imageView.properties["cardforge.collectionPresentation"]=collectionPresentation }
        updateImageView(imageView,image,data,template);viewport.children.add(imageView);if(showCropGuides)addCropGuides(viewport)
        val foregroundBorder=Rectangle(template.art.width,template.art.height).apply{isMouseTransparent=true;fill=Color.TRANSPARENT;stroke=Color.web(data.accentColor);strokeWidth=4.0;arcWidth=template.art.radius;arcHeight=template.art.radius}
        artFrame.children.addAll(frameRect,viewport,foregroundBorder);position(artFrame,template.art);content.children.add(artFrame);installImageInteractions(viewport,onImageDragged,onImageZoomed,onImageReset)

        val typeBox=railBox(template.typeBox,data.backgroundColor,palette.outerFrame,visual,fgOpacity);position(typeBox,template.typeBox)
        val typeSize=CardVisualSystem.fitFontSize(data.typeLine,18.0,13.0,template.typeText.width,template.typeText.height,true)
        val typeLabel=label(data.typeLine,typeSize,true,data.darkTextColor);place(typeLabel,template.typeText)
        val rarityDisplay="◆ ${data.rarity}"
        val raritySize=CardVisualSystem.fitFontSize(rarityDisplay,15.0,9.5,template.rarityText.width,template.rarityText.height,true)
        val rarityLabel=label(rarityDisplay,raritySize,true,palette.outerFrame).apply {
            isWrapText=false
            textOverrun=OverrunStyle.CLIP
        };place(rarityLabel,template.rarityText);content.children.addAll(typeBox,typeLabel,rarityLabel)

        val descOpacity=(data.panelOpacity.coerceIn(0.0,1.0)*fgOpacity).coerceIn(0.0,1.0)
        val descPanel=descriptionPanel(template.descriptionBox,data.panelColor,palette.outerFrame,visual,descOpacity);position(descPanel,template.descriptionBox);content.children.add(descPanel)
        val headingSize=CardVisualSystem.fitFontSize(collectionPresentation.descriptionHeading,17.0,13.0,template.descriptionHeading.width,template.descriptionHeading.height,true)
        val heading=label(collectionPresentation.descriptionHeading,headingSize,true,data.textColor);place(heading,template.descriptionHeading)
        val bodySize=CardVisualSystem.fitFontSize(data.description,data.bodyFontSize,11.5,template.descriptionText.width,template.descriptionText.height)
        val description=label(data.description,bodySize,false,data.textColor);description.font=Font.font("Georgia",FontWeight.NORMAL,bodySize);place(description,template.descriptionText)
        val flavor=label(data.flavorText,14.0,false,data.textColor).apply{font=Font.font("Georgia",FontPosture.ITALIC,14.0)};place(flavor,template.flavorText)
        val copyright=if(collectionPresentation.showArtistCopyright)"© " else ""
        val footer=label("${data.setName} • ${data.collectorNumber} • ${copyright}${data.artist}",10.0,false,data.textColor);place(footer,template.footerText);content.children.addAll(heading,description,flavor,footer)

        val statsBox=statsJewel(template.statsBox,data.backgroundColor,palette.outerFrame,visual,fgOpacity);position(statsBox,template.statsBox)
        val statsSize=CardVisualSystem.fitFontSize(data.stats,25.0,17.0,template.statsText.width,template.statsText.height,true)
        val stats=label(data.stats,statsSize,true,data.darkTextColor);place(stats,template.statsText);content.children.addAll(statsBox,stats)
        outer.children.add(content)
        if(backgroundOverlay!=null&&data.backgroundOverlayPlacement==OverlayPlacement.OVER_CONTENT)outer.children.add(overlayLayer(backgroundOverlay,template,data))
        root.children.add(outer)
        return Rendered(root,imageView,viewport,template.width,template.height)
    }

    private fun materialFrameLayer(template: CardTemplate, data: CardData, palette: CardVisualPalette): Pane = Pane().apply {
        isManaged = false
        resize(template.width, template.height)
        relocate(0.0, 0.0)
        isMouseTransparent = true
        val visual = template.visualStyle
        val first = visual.outerFrameInset.coerceAtLeast(data.borderWidth / 2.0 + 2.0)
        val second = visual.innerFrameInset.coerceAtLeast(first + 4.0)
        children.add(Rectangle(first, first, (template.width-first*2).coerceAtLeast(0.0), (template.height-first*2).coerceAtLeast(0.0)).apply {
            val radius=(data.cornerRadius-first).coerceAtLeast(4.0)
            arcWidth=radius*2; arcHeight=radius*2; fill=Color.TRANSPARENT
            stroke=Color.web(palette.outerFrameSecondary); strokeWidth=1.6; opacity=0.78
        })
        children.add(Rectangle(second, second, (template.width-second*2).coerceAtLeast(0.0), (template.height-second*2).coerceAtLeast(0.0)).apply {
            val radius=(data.cornerRadius-second).coerceAtLeast(3.0)
            arcWidth=radius*2; arcHeight=radius*2; fill=Color.TRANSPARENT
            stroke=Color.web(CardVisualSystem.lighten(palette.outerFrame,0.18)); strokeWidth=0.9; opacity=0.42
        })
    }

    private fun railBox(rect:TemplateRect,fill:String,stroke:String,visual:TemplateVisualStyle,opacity:Double)=StackPane().apply{
        isManaged=false;prefWidth=rect.width;prefHeight=rect.height;minWidth=rect.width;minHeight=rect.height;maxWidth=rect.width;maxHeight=rect.height;resize(rect.width,rect.height)
        val top=CardVisualSystem.lighten(fill,0.07); val bottom=CardVisualSystem.darken(fill,0.12)
        style="-fx-background-color:linear-gradient(to bottom,${top.withOpacity(opacity)},${fill.withOpacity(opacity)} 52%,${bottom.withOpacity(opacity)});-fx-background-radius:${rect.radius}px;-fx-border-color:$stroke;-fx-border-width:${visual.railStrokeWidth}px;-fx-border-radius:${rect.radius}px;-fx-effect:dropshadow(gaussian,rgba(0,0,0,0.22),${visual.railDepth*1.5},0,${visual.railDepth},${visual.railDepth});"
    }

    private fun descriptionPanel(rect:TemplateRect,fill:String,stroke:String,visual:TemplateVisualStyle,opacity:Double)=StackPane().apply{
        isManaged=false;prefWidth=rect.width;prefHeight=rect.height;minWidth=rect.width;minHeight=rect.height;maxWidth=rect.width;maxHeight=rect.height;resize(rect.width,rect.height)
        val top=CardVisualSystem.lighten(fill,0.05); val bottom=CardVisualSystem.darken(fill,0.08)
        style="-fx-background-color:linear-gradient(to bottom,${top.withOpacity(opacity)},${fill.withOpacity(opacity)} 28%,${bottom.withOpacity(opacity)});-fx-background-radius:${rect.radius}px;-fx-border-color:${CardVisualSystem.mix(stroke,fill,0.28)} $stroke;-fx-border-width:1px 2px 3px 2px;-fx-border-radius:${rect.radius}px;-fx-effect:dropshadow(gaussian,rgba(0,0,0,0.28),${visual.descriptionDepth*1.6},0,0,${visual.descriptionDepth});"
    }

    private fun statsJewel(rect:TemplateRect,fillColor:String,strokeColor:String,visual:TemplateVisualStyle,alpha:Double)=Pane().apply{
        isManaged=false;prefWidth=rect.width;prefHeight=rect.height;minWidth=rect.width;minHeight=rect.height;maxWidth=rect.width;maxHeight=rect.height;resize(rect.width,rect.height)
        val cut=visual.statsJewelCut.coerceIn(4.0,minOf(rect.width,rect.height)/3.0)
        val outer=Polygon(cut,0.0,rect.width-cut,0.0,rect.width,cut,rect.width,rect.height-cut,rect.width-cut,rect.height,cut,rect.height,0.0,rect.height-cut,0.0,cut).apply{
            fill=Color.web(fillColor,alpha);stroke=Color.web(strokeColor);strokeWidth=2.2
        }
        val inset=visual.statsJewelInset.coerceAtLeast(2.0)
        val innerCut=(cut-inset/2.0).coerceAtLeast(2.0)
        val w=(rect.width-inset*2).coerceAtLeast(1.0);val h=(rect.height-inset*2).coerceAtLeast(1.0)
        val inner=Polygon(innerCut,0.0,w-innerCut,0.0,w,innerCut,w,h-innerCut,w-innerCut,h,innerCut,h,0.0,h-innerCut,0.0,innerCut).apply{
            relocate(inset,inset);fill=Color.TRANSPARENT;stroke=Color.web(CardVisualSystem.lighten(strokeColor,0.22));strokeWidth=0.9;opacity=0.62
        }
        children.addAll(outer,inner)
    }

    private fun decorativeBox(rect:TemplateRect,fill:String,stroke:String,strokeWidth:Double,opacity:Double=1.0)=StackPane().apply{
        isManaged=false;prefWidth=rect.width;prefHeight=rect.height;minWidth=rect.width;minHeight=rect.height;maxWidth=rect.width;maxHeight=rect.height;resize(rect.width,rect.height)
        style="-fx-background-color:${fill.withOpacity(opacity)};-fx-background-radius:${rect.radius}px;-fx-border-color:$stroke;-fx-border-width:${strokeWidth}px;-fx-border-radius:${rect.radius}px;"
    }
    private fun String.withOpacity(opacity:Double):String=runCatching{val c=Color.web(this);"rgba(${(c.red*255).toInt()},${(c.green*255).toInt()},${(c.blue*255).toInt()},${opacity.coerceIn(0.0,1.0)})"}.getOrElse{this}
    private fun place(node:javafx.scene.layout.Region,spec:TemplateText){node.isManaged=false;node.prefWidth=spec.width;node.prefHeight=spec.height;node.minWidth=spec.width;node.minHeight=spec.height;node.maxWidth=spec.width;node.maxHeight=spec.height;node.resize(spec.width,spec.height);node.relocate(spec.x,spec.y);if(node is Label)node.alignment=when(spec.align.uppercase()){ "CENTER"->Pos.CENTER;"RIGHT"->Pos.CENTER_RIGHT;else->Pos.CENTER_LEFT}}
    private fun position(node:javafx.scene.Node,rect:TemplateRect){node.isManaged=false;node.relocate(rect.x,rect.y);if(node is javafx.scene.layout.Region)node.resize(rect.width,rect.height)}
    private fun overlayLayer(image:Image,template:CardTemplate,data:CardData)=ImageView(image).apply{isPreserveRatio=false;fitWidth=template.width;fitHeight=template.height;opacity=data.backgroundOverlayOpacity.coerceIn(0.0,1.0);isMouseTransparent=true}

    data class ImageLayout(val width:Double,val height:Double,val x:Double,val y:Double,val minOffsetX:Double,val maxOffsetX:Double,val minOffsetY:Double,val maxOffsetY:Double)
    fun imageLayout(image:Image?,data:CardData,template:CardTemplate):ImageLayout{
        if(image==null||image.width<=0.0||image.height<=0.0)return ImageLayout(template.art.width,template.art.height,0.0,0.0,0.0,0.0,0.0,0.0)
        val sourceW=image.width;val sourceH=image.height
        val (width,height)=when(data.imageMode){
            ImageMode.STRETCH->template.art.width*data.imageZoom to template.art.height*data.imageZoom
            ImageMode.COVER,ImageMode.CONTAIN->{val base=if(data.imageMode==ImageMode.COVER)max(template.art.width/sourceW,template.art.height/sourceH) else min(template.art.width/sourceW,template.art.height/sourceH);sourceW*base*data.imageZoom to sourceH*base*data.imageZoom}
        }
        val centeredX=(template.art.width-width)/2.0;val centeredY=(template.art.height-height)/2.0
        val minX=min(0.0,template.art.width-width)-centeredX;val maxX=max(0.0,template.art.width-width)-centeredX
        val minY=min(0.0,template.art.height-height)-centeredY;val maxY=max(0.0,template.art.height-height)-centeredY
        val ox=data.imageOffsetX.coerceIn(minX,maxX);val oy=data.imageOffsetY.coerceIn(minY,maxY)
        return ImageLayout(width,height,centeredX+ox,centeredY+oy,minX,maxX,minY,maxY)
    }
    fun updateImageView(view:ImageView,image:Image?,data:CardData,template:CardTemplate){val l=imageLayout(image,data,template);view.isPreserveRatio=false;view.fitWidth=l.width;view.fitHeight=l.height;view.translateX=l.x;view.translateY=l.y;(view.properties["cardforge.bleedImageView"] as? ImageView)?.let{val p=view.properties["cardforge.collectionPresentation"] as? CollectionPresentation?:CollectionPresentation();updateBleedImageView(it,image,data,template,p)}}
    private fun updateBleedImageView(view:ImageView,image:Image?,data:CardData,template:CardTemplate,presentation:CollectionPresentation){val l=imageLayout(image,data,template);view.isPreserveRatio=false;view.opacity=CollectionVisualSettings.bleedOpacity(data,presentation);view.fitWidth=l.width;view.fitHeight=l.height;view.translateX=template.art.x+l.x;view.translateY=template.art.y+l.y}

    private fun addCropGuides(viewport:StackPane){val guide=StackPane().apply{isMouseTransparent=true};val w=viewport.prefWidth;val h=viewport.prefHeight;val v=Line(w/2,0.0,w/2,h);val hz=Line(0.0,h/2,w,h/2);listOf(v,hz).forEach{it.stroke=Color.rgb(255,255,255,0.28);it.strokeWidth=1.0};guide.children.addAll(v,hz);val help=Label("DRAG TO POSITION  •  SCROLL TO ZOOM").apply{isMouseTransparent=true;textFill=Color.rgb(255,255,255,0.82);style="-fx-background-color:rgba(0,0,0,0.42);-fx-background-radius:8px;-fx-padding:5 8 5 8;-fx-font-size:11px;-fx-font-weight:bold;"};StackPane.setAlignment(help,Pos.BOTTOM_CENTER);StackPane.setMargin(help,Insets(0.0,0.0,10.0,0.0));guide.children.add(help);viewport.children.add(guide)}
    private fun installImageInteractions(viewport:StackPane,onDragged:(Double,Double)->Unit,onZoomed:(Double)->Unit,onReset:()->Unit){
        var lastX=0.0;var lastY=0.0;var dragging=false;var scene:javafx.scene.Scene?=null
        val dragHandler=javafx.event.EventHandler<MouseEvent>{e->if(dragging&&e.isPrimaryButtonDown){val previous=viewport.sceneToLocal(lastX,lastY);val current=viewport.sceneToLocal(e.sceneX,e.sceneY);val dx=current.x-previous.x;val dy=current.y-previous.y;lastX=e.sceneX;lastY=e.sceneY;if(dx!=0.0||dy!=0.0)onDragged(dx,dy);e.consume()}}
        lateinit var releaseHandler:javafx.event.EventHandler<MouseEvent>
        releaseHandler=javafx.event.EventHandler{e->dragging=false;viewport.cursor=Cursor.OPEN_HAND;scene?.removeEventFilter(MouseEvent.MOUSE_DRAGGED,dragHandler);scene?.removeEventFilter(MouseEvent.MOUSE_RELEASED,releaseHandler);scene=null;e.consume()}
        viewport.cursor=Cursor.OPEN_HAND
        viewport.addEventFilter(MouseEvent.MOUSE_PRESSED){e->if(e.button==MouseButton.PRIMARY){lastX=e.sceneX;lastY=e.sceneY;dragging=true;viewport.cursor=Cursor.CLOSED_HAND;scene=viewport.scene;scene?.addEventFilter(MouseEvent.MOUSE_DRAGGED,dragHandler);scene?.addEventFilter(MouseEvent.MOUSE_RELEASED,releaseHandler);e.consume()}}
        viewport.addEventHandler(MouseEvent.MOUSE_CLICKED){e->if(e.button==MouseButton.PRIMARY&&e.clickCount==2){onReset();e.consume()}}
        viewport.addEventHandler(ScrollEvent.SCROLL){e->if(e.deltaY!=0.0){onZoomed(if(e.deltaY>0)0.1 else -0.1);e.consume()}}
    }
}
