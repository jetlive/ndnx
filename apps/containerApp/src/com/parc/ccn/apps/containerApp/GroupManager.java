package com.parc.ccn.apps.containerApp;

import java.awt.EventQueue;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;

import javax.swing.JButton;

import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.border.BevelBorder;

public class GroupManager extends JDialog implements ActionListener{

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private JList groupsList;
	private JList groupMembersList;
	private JList usersList;

	private JButton applyChangesButton;
	private JButton revertChangesButton;
	private JButton cancelChangesButton;
	private JButton addButton;
	private JButton removeButton;
	private JLabel groupMembersLabel;
	
	private String path;
	
	private SortedListModel groupsModel = null;
	private SortedListModel groupsMembersModel = null;
	private SortedListModel userPool = null;

	//Default Items
	private SortedListModel groupsModelDefault = null;
	private SortedListModel groupsMembersModelDefault = null;
	private SortedListModel userPoolDefault = null;
	
	//ArrayList of groups and Members
	private ArrayList<SortedListModel> groups = null; 
	private ArrayList<SortedListModel> groupsDefault = null;
	//Get the list of users
	public String[] getUserList(String path2,String permissions)
	{
		if(permissions ==null){
		
			//get all users/groups
			String principals[] = {"Paulo","Noel","Eliza","Chico","Alex","Aaron","Kelly"};
			return principals;			
		}
		else {
			//Use the path to look up the permissions
			if(permissions.equalsIgnoreCase("groups"))
			{
				String principals[] = {"CSL","CCN","STIR","HSL","ASC"};
				return principals;
			}
			else if (permissions.equalsIgnoreCase("group_members"))
			{
				String principals[] = {"Alice","Fred","Bob","Frank","Matsumoto","Jorge","Frederick","Jane"};
				return principals;
			}
		
		}
		String empty[] ={""};
		return empty;
	}
	/**
	 * Create the dialog
	 * @param frame 
	 * @param path 
	 */
	public GroupManager(String path) {
		super();
		this.path = path;
		
		groupsModel = new SortedListModel();
		groupsMembersModel = new SortedListModel();
		userPool = new SortedListModel();
		
		groupsModelDefault = new SortedListModel();
		groupsMembersModelDefault = new SortedListModel();
		userPoolDefault = new SortedListModel();
		
		//List Models for the List Objects
		userPool.addAll(getUserList(path,null));
		groupsModel.addAll(getUserList(path,"groups"));
		groupsMembersModel.addAll(getUserList(path,"group_members"));
		
		//Default List Items
		userPoolDefault.addAll(getUserList(path,null));
		groupsMembersModelDefault.addAll(getUserList(path,"group_members"));
		groupsModelDefault.addAll(getUserList(path,"groups"));
		
		
		setTitle("Manage Group Members");
		getContentPane().setLayout(null);
		setBounds(100, 100, 523, 439);

		applyChangesButton = new JButton();
		applyChangesButton.addActionListener(this);
		applyChangesButton.setMargin(new Insets(2, 2, 2, 2));
		applyChangesButton.setBounds(47, 362, 112, 25);
		applyChangesButton.setText("Apply Changes");
		getContentPane().add(applyChangesButton);

		revertChangesButton = new JButton();
		revertChangesButton.addActionListener(this);
		revertChangesButton.setMargin(new Insets(2, 2, 2, 2));
		revertChangesButton.setText("Revert Changes");
		revertChangesButton.setBounds(218, 362, 112, 25);
		getContentPane().add(revertChangesButton);

		cancelChangesButton = new JButton();
		cancelChangesButton.addActionListener(this);
		cancelChangesButton.setMargin(new Insets(2, 2, 2, 2));
		cancelChangesButton.setText("Cancel Changes");
		cancelChangesButton.setBounds(363, 362, 112, 25);
		getContentPane().add(cancelChangesButton);

		addButton = new JButton();
		addButton.addActionListener(this);
		addButton.setText("add ->");
		addButton.setBounds(218, 145, 112, 25);
		getContentPane().add(addButton);

		removeButton = new JButton();
		removeButton.addActionListener(this);
		removeButton.setText("<- remove");
		removeButton.setBounds(218, 302, 112, 25);
		getContentPane().add(removeButton);

		final JLabel groupMembersLabel = new JLabel();
		groupMembersLabel.setAutoscrolls(true);
		groupMembersLabel.setText("List of Users");
		groupMembersLabel.setBounds(47, 120, 98, 15);
		getContentPane().add(groupMembersLabel);

		final JLabel usersLabel = new JLabel();
		usersLabel.setAutoscrolls(true);
		usersLabel.setText("Group Members");
		usersLabel.setBounds(375, 120, 100, 15);
		getContentPane().add(usersLabel);

		final JScrollPane scrollPaneUsers = new JScrollPane();
		scrollPaneUsers.setBounds(45, 147, 120, 181);
		getContentPane().add(scrollPaneUsers);

		final JScrollPane scrollPaneGroupMembers = new JScrollPane();
		scrollPaneGroupMembers.setBounds(375, 147, 120, 181);
		getContentPane().add(scrollPaneGroupMembers);

		final JScrollPane scrollPaneGroups = new JScrollPane();
		scrollPaneGroups.setBounds(76, 37, 388, 58);
		getContentPane().add(scrollPaneGroups);

		usersList = new JList(userPool);
		scrollPaneUsers.setViewportView(usersList);
		usersList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
		usersList.setBorder(new BevelBorder(BevelBorder.LOWERED));
//		usersList.setBounds(45, 147, 120, 181);
//		getContentPane().add(usersList);

		groupMembersList = new JList(groupsMembersModel);
		scrollPaneGroupMembers.setViewportView(groupMembersList);
		groupMembersList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
		groupMembersList.setBorder(new BevelBorder(BevelBorder.LOWERED));
//		groupMembersList.setBounds(375, 147, 120, 181);
//		getContentPane().add(groupMembersList);

		//single group selection for now
		groupsList = new JList(groupsModel);
		scrollPaneGroups.setViewportView(groupsList);
		groupsList.setBorder(new BevelBorder(BevelBorder.LOWERED));
//		groupsList.setBounds(76, 37, 388, 58);
//		getContentPane().add(groupsList);

		final JLabel groupsLabel = new JLabel();
		groupsLabel.setText("Groups");
		groupsLabel.setBounds(76, 10, 69, 15);
		getContentPane().add(groupsLabel);

			}
	
	public void actionPerformed(ActionEvent e) {
		// TODO Auto-generated method stub
		if(applyChangesButton == e.getSource()) {
			
			applyChanges();
		}else if(revertChangesButton == e.getSource()){
			
			restoreDefaults();
			
			
		}else if(cancelChangesButton == e.getSource()){
			
			cancelChanges();
			
		}else if(addButton == e.getSource()){
			moveListItems(usersList.getSelectedIndices(),usersList,groupMembersList);
			
		}else if(removeButton == e.getSource()){			
			moveListItems(groupMembersList.getSelectedIndices(),groupMembersList,usersList);			
			
		}
		
	}
	private void applyChanges() {
		// TODO Take all the elements and assign them new acls somehow
		
		
	}

	private void restoreDefaults() {

		//Clear out the old model
		userPool.clear();
		groupsModel.clear();
		groupsMembersModel.clear();
		
		//In with the new
		userPool.addAll(userPoolDefault.getAllElements());
		groupsModel.addAll(groupsModelDefault.getAllElements());
		groupsMembersModel.addAll(groupsMembersModelDefault.getAllElements());
		
	}
	
	private void cancelChanges()
	{
		this.setVisible(false);
		this.dispose();
	}
	
	private void moveListItems(int selectedIndices[],JList fromList,JList toList)
	{
		//ArrayList of selected items
		ArrayList<Object> itemsSelected = new ArrayList<Object>();
		
		//Items selected that are to be removed from the toList
		//ArrayList<Object> itemsToBeRemoved = new ArrayList<Object>();
		
		for(int i=0;i<selectedIndices.length;i++)
		{
			//remove item from fromList and move to toList
			System.out.println("Index is "+ selectedIndices[i]);
			Object selectedItem = fromList.getModel().getElementAt(selectedIndices[i]);
			itemsSelected.add(selectedItem);			
			
		}
		
		//Bulk adding and removal of items
		
		for(Object item : itemsSelected)
		{
			((SortedListModel)toList.getModel()).add(item);
			((SortedListModel)fromList.getModel()).removeElement(item);
			
		}
		
	}

}